package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerEntry;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.integration.payos.PayOsGateway;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.session.service.SessionService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentOrderService, focusing on:
 * 1. Webhook signature verification via PayOsGateway (HMAC done inside SdkPayOsGateway)
 * 2. Idempotency guards (duplicate eventId, already-final order)
 * 3. Checkout: credit-covers-all path (no PayOS call needed)
 *
 * Security note: Actual HMAC-SHA256 verification against PayOS's checksumKey is
 * tested indirectly here by mocking PayOsGateway.verifyWebhook(). The real
 * HMAC logic lives in SdkPayOsGateway and is covered by integration tests.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private CouponService couponService;
    @Mock private CreditLedgerService creditLedgerService;
    @Mock private CampaignService campaignService;
    @Mock private PayOsGateway payOsGateway;
    @Mock private SettlementService settlementService;
    @Mock private SessionService sessionService;
    @Mock private com.fptu.exe.skillswap.modules.conversation.service.ConversationService conversationService;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentOrderService paymentOrderService;

    private UUID menteeId;
    private UUID mentorId;
    private UUID bookingId;
    private Booking booking;

    @BeforeEach
    void setUp() {
        PaymentProperties paymentProperties = new PaymentProperties();
        paymentProperties.getPayos().setReturnUrl("https://skillswap.asia/payment/return");
        paymentProperties.getPayos().setCancelUrl("https://skillswap.asia/payment/cancel");
        paymentProperties.getPayos().setChecksumKey("test-checksum-key");

        paymentOrderService = new PaymentOrderService(
                bookingRepository,
                paymentOrderRepository,
                paymentAttemptRepository,
                couponService,
                creditLedgerService,
                campaignService,
                paymentProperties,
                payOsGateway,
                settlementService,
                sessionService,
                conversationService,
                notificationService,
                eventPublisher
        );

        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        User mentee = new User();
        mentee.setId(menteeId);
        mentee.setFullName("Test Mentee");

        User mentorUser = new User();
        mentorUser.setId(mentorId);
        mentorUser.setEmail("mentor@test.com");
        mentorUser.setFullName("Test Mentor");

        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .user(mentorUser)
                .build();

        booking = Booking.builder()
                .id(bookingId)
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .status(BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                .servicePriceScoinSnapshot(100)
                .build();
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────

    private PaymentWebhookRequest buildWebhookRequest(Long orderCode, String signature) {
        PaymentWebhookRequest.PaymentWebhookDataRequest data = new PaymentWebhookRequest.PaymentWebhookDataRequest(
                orderCode,   // orderCode
                null,        // amount
                null,        // description
                null,        // accountNumber
                null,        // reference
                null,        // transactionDateTime
                null,        // currency
                null,        // paymentLinkId
                "00",        // code (success)
                "success",   // desc
                null, null, null, null, null, null
        );
        return new PaymentWebhookRequest("00", "success", true, data, signature);
    }

    private PayOsGateway.VerifiedWebhook verifiedWebhook(String orderCode, String transactionId) {
        return new PayOsGateway.VerifiedWebhook(
                orderCode,
                "pl-" + orderCode,
                "evt-" + orderCode,
                transactionId,
                "PAID",
                true,
                LocalDateTime.now()
        );
    }

    // ─── Checkout ────────────────────────────────────────────────────────────────

    @Test
    void checkout_creditFullyCovers_shouldCompleteInternallyWithoutPaymentLink() {
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(couponService.resolveCoupon(null)).thenReturn(null);
        when(campaignService.resolveCampaignCredit(eq(menteeId), eq(booking), eq(100)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());
        when(creditLedgerService.reserveCredit(eq(menteeId), eq(100), eq(LedgerSourceType.PAYMENT_ORDER), any(), any(), any()))
                .thenReturn(List.of(CreditLedgerEntry.builder()
                        .amountScoin(100)
                        .originType(CreditOriginType.MANUAL)
                        .build()));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentAttemptRepository.countByPaymentOrderId(any())).thenReturn(0L);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.checkout(menteeId, new PaymentCheckoutRequest(bookingId, null));

        assertEquals(PaymentOrderStatus.PAID, response.status());
        assertEquals(0, response.remainingPayableScoin());
        assertNull(response.paymentLink());
        verify(creditLedgerService).consumeReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), any(), any());
        // PayOsGateway must NOT be called for credit-covered checkout
        verify(payOsGateway, never()).createPaymentLink(any());
    }

    @Test
    void handleMenteeCancellation_awaitingPayment_shouldRollbackReservedCreditAndVoidCoupon() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-CANCEL-1")
                .bookingId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();

        when(paymentOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMenteeCancellation(booking, false);

        assertEquals(PaymentOrderStatus.CANCELLED, order.getStatus());
        assertNotNull(order.getCancelledAt());
        verify(creditLedgerService).releaseReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
        verify(couponService).voidRedemption(order.getId());
        verifyNoInteractions(settlementService);
    }

    @Test
    void handleMenteeCancellation_paid_shouldDelegateToSettlement() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-CANCEL-2")
                .bookingId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.PAID)
                .build();

        when(paymentOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMenteeCancellation(booking, true);

        assertNotNull(order.getCancelledAt());
        verify(settlementService).handlePaidBookingCancelledByMentee(eq(booking), eq(order), eq(true));
    }

    @Test
    void handleMentorCancellation_awaitingPayment_shouldRollbackReservedCreditAndVoidCoupon() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-MENTOR-CANCEL-1")
                .bookingId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();

        when(paymentOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMentorCancellation(booking);

        assertEquals(PaymentOrderStatus.CANCELLED, order.getStatus());
        verify(creditLedgerService).releaseReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
        verify(couponService).voidRedemption(order.getId());
        verifyNoInteractions(settlementService);
    }

    @Test
    void handleMentorCancellation_paid_shouldDelegateFullRefundToSettlement() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-MENTOR-CANCEL-2")
                .bookingId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.PAID)
                .build();

        when(paymentOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMentorCancellation(booking);

        assertNotNull(order.getCancelledAt());
        verify(settlementService).handlePaidBookingCancelledByMentor(eq(booking), eq(order));
    }

    // ─── Webhook: valid signature (gateway returns success) ──────────────────────

    @Test
    void handleWebhook_paid_gatewayVerifiesOk_shouldConsumeReservedCreditAndMarkSucceeded() {
        Long orderCode = 123456789L;
        PaymentWebhookRequest webhookRequest = buildWebhookRequest(orderCode, "valid-hmac-signature");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-TEST")
                .bookingId(bookingId)
                .providerOrderCode(String.valueOf(orderCode))
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(200)
                .remainingPayableScoin(50)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .build();

        // Gateway returns verified result (HMAC is correct per SdkPayOsGateway)
        when(payOsGateway.verifyWebhook(webhookRequest)).thenReturn(verifiedWebhook(String.valueOf(orderCode), "txn-1"));
        when(paymentAttemptRepository.findByProviderOrderCodeForUpdate(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentOrderRepository.existsByProviderEventId("evt-" + orderCode)).thenReturn(false);
        when(paymentAttemptRepository.existsByProviderEventId("evt-" + orderCode)).thenReturn(false);
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.handleWebhook(webhookRequest);

        assertEquals(PaymentOrderStatus.PAID, response.status());
        verify(creditLedgerService).consumeReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
        verify(couponService).markRedeemed(order.getId());

        ArgumentCaptor<PaymentAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentAttemptRepository).save(attemptCaptor.capture());
        assertEquals(PaymentAttemptStatus.SUCCEEDED, attemptCaptor.getValue().getStatus());
    }

    // ─── Webhook security: invalid signature ─────────────────────────────────────

    /**
     * Critical security test: attacker sends forged webhook — PayOS SDK signature check throws PayOSException.
     * SdkPayOsGateway wraps this as UNAUTHORIZED BaseException.
     * No credit must be consumed, no order must be saved.
     */
    @Test
    void handleWebhook_invalidSignature_gatewayThrows_shouldPropagateUnauthorized() {
        Long orderCode = 999999L;
        PaymentWebhookRequest forgedRequest = buildWebhookRequest(orderCode, "FORGED_SIGNATURE");

        // Simulate what SdkPayOsGateway does when PayOS SDK rejects the signature
        when(payOsGateway.verifyWebhook(forgedRequest))
                .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, "Webhook PayOS không hợp lệ hoặc sai chữ ký"));

        BaseException ex = assertThrows(BaseException.class,
                () -> paymentOrderService.handleWebhook(forgedRequest));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        // No financial mutation must have occurred
        verify(creditLedgerService, never()).consumeReservedCredit(any(), any(), any(), any());
        verify(paymentOrderRepository, never()).save(any());
        verify(paymentAttemptRepository, never()).save(any());
    }

    // ─── Webhook: BAD_REQUEST for non-success payload ────────────────────────────

    @Test
    void handleWebhook_providerStatusNotPaid_shouldThrowBadRequest() {
        Long orderCode = 111L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");

        // Gateway verifies signature OK but status is not PAID (e.g. CANCELLED)
        PayOsGateway.VerifiedWebhook notPaid = new PayOsGateway.VerifiedWebhook(
                String.valueOf(orderCode), "pl-111", "evt-111", "txn-111",
                "CANCELLED",  // not PAID
                false,        // success = false
                null
        );
        when(payOsGateway.verifyWebhook(request)).thenReturn(notPaid);

        BaseException ex = assertThrows(BaseException.class,
                () -> paymentOrderService.handleWebhook(request));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(creditLedgerService, never()).consumeReservedCredit(any(), any(), any(), any());
    }

    // ─── Webhook idempotency: duplicate eventId ───────────────────────────────────

    @Test
    void handleWebhook_duplicateEventId_shouldReturnEarlyWithoutReprocessing() {
        Long orderCode = 222L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .providerOrderCode(String.valueOf(orderCode))
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .remainingPayableScoin(0)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .build();

        when(payOsGateway.verifyWebhook(request)).thenReturn(verifiedWebhook(String.valueOf(orderCode), "txn-dup"));
        when(paymentAttemptRepository.findByProviderOrderCodeForUpdate(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        // Simulate already-processed event
        when(paymentOrderRepository.existsByProviderEventId("evt-" + orderCode)).thenReturn(true);

        paymentOrderService.handleWebhook(request);

        // Must short-circuit — no financial mutation
        verify(creditLedgerService, never()).consumeReservedCredit(any(), any(), any(), any());
        verify(paymentOrderRepository, never()).save(any());
    }

    // ─── Webhook idempotency: order already in final state ────────────────────────

    @Test
    void handleWebhook_orderAlreadyPaid_shouldReturnEarlyWithoutReprocessing() {
        Long orderCode = 333L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .providerOrderCode(String.valueOf(orderCode))
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .remainingPayableScoin(0)
                .status(PaymentOrderStatus.PAID) // already finalized
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.SUCCEEDED)
                .build();

        when(payOsGateway.verifyWebhook(request)).thenReturn(verifiedWebhook(String.valueOf(orderCode), "txn-final"));
        when(paymentAttemptRepository.findByProviderOrderCodeForUpdate(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.existsByProviderEventId(any())).thenReturn(false);
        when(paymentAttemptRepository.existsByProviderEventId(any())).thenReturn(false);

        paymentOrderService.handleWebhook(request);

        verify(creditLedgerService, never()).consumeReservedCredit(any(), any(), any(), any());
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void checkout_freeService_shouldThrowBadRequest() {
        User menteeUser = new User();
        menteeUser.setId(menteeId);
        menteeUser.setFullName("Mentee");

        User mentorUser = new User();
        mentorUser.setId(mentorId);
        mentorUser.setFullName("Mentor");

        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorId);
        mentorProfile.setUser(mentorUser);

        Booking freeBooking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(menteeUser)
                .mentorProfile(mentorProfile)
                .status(BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                .serviceIsFreeSnapshot(true)
                .servicePriceScoinSnapshot(0)
                .build();

        when(bookingRepository.findByIdForSessionUpdate(freeBooking.getId())).thenReturn(Optional.of(freeBooking));

        PaymentCheckoutRequest request = new PaymentCheckoutRequest(freeBooking.getId(), null);

        BaseException exception = assertThrows(BaseException.class, () -> 
                paymentOrderService.checkout(menteeId, request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("Không cần thanh toán cho dịch vụ miễn phí", exception.getMessage());
    }
}
