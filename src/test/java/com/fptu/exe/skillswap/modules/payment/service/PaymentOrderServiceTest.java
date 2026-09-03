package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingCancellationContext;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSnapshot;
import com.fptu.exe.skillswap.modules.booking.service.BookingQueryPortImpl;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerEntry;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.integration.payos.PayOsGateway;
import com.fptu.exe.skillswap.modules.payment.integration.payos.PayOsPaymentGatewayProvider;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.Instant;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserQueryPort userQueryPort;
    @Mock
    private BookingPaymentSettlementPort bookingPaymentSettlementPort;
    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private CouponService couponService;
    @Mock
    private CreditLedgerService creditLedgerService;
    @Mock
    private CampaignService campaignService;
    @Mock
    private PayOsGateway payOsGateway;
    @Mock
    private SettlementService settlementService;
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private InternalTelemetryService internalTelemetryService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PaymentOrderService paymentOrderService;
    private PaymentProperties paymentProperties;
    private UUID menteeId;
    private UUID mentorId;
    private UUID bookingId;
    private Booking booking;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();
        paymentProperties.getPayos().setReturnUrl("https://skillswap.asia/payment/return");
        paymentProperties.getPayos().setCancelUrl("https://skillswap.asia/payment/cancel");
        paymentProperties.getPayos().setChecksumKey("test-checksum-key");
        paymentProperties.setMenteeSurchargeBps(0);
        paymentProperties.setMentorCommissionBps(0);

        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        paymentOrderService = new PaymentOrderService(
                new BookingQueryPortImpl(bookingRepository),
                userQueryPort,
                bookingPaymentSettlementPort,
                paymentOrderRepository,
                paymentAttemptRepository,
                couponService,
                creditLedgerService,
                campaignService,
                paymentProperties,
                new PaymentGatewayProviderFactory(List.of(new PayOsPaymentGatewayProvider(payOsGateway))),
                settlementService,
                eventPublisher,
                internalTelemetryService,
                transactionTemplate
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
                .build();

        booking = Booking.builder()
                .id(bookingId)
                .menteeUserId(mentee.getId())
                .mentorUserId(mentorId)
                .status(BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                .serviceIsFreeSnapshot(false)
                .serviceDurationSnapshot(60)
                .servicePriceScoinSnapshot(72_000)
                .build();
        org.mockito.Mockito.lenient().when(bookingPaymentSettlementPort.findPaymentSnapshotForUpdate(bookingId))
                .thenReturn(Optional.of(new BookingPaymentSnapshot(
                        bookingId, menteeId, mentorId, null, 72_000, false,
                        "ACCEPTED_AWAITING_PAYMENT", Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(900))));
        org.mockito.Mockito.lenient().when(bookingPaymentSettlementPort.findCancellationContext(bookingId))
                .thenReturn(Optional.of(new BookingCancellationContext(
                        bookingId, menteeId, mentorId, "CANCELLED_BY_MENTEE", null,
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                        Instant.now(), false, true)));
        java.util.Map<CreditOriginType, Integer> defaultBalances = new java.util.EnumMap<>(CreditOriginType.class);
        for (CreditOriginType type : CreditOriginType.values()) {
            defaultBalances.put(type, 1000000);
        }
        org.mockito.Mockito.lenient().when(creditLedgerService.getAvailableBalanceByOrigin(any())).thenReturn(defaultBalances);
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
                LocalDateTime.now(),
                100_000L
        );
    }

    // ─── Checkout ────────────────────────────────────────────────────────────────

    @Test
    void checkout_creditFullyCovers_shouldCompleteInternallyWithoutPaymentLink() {
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.empty());
        when(couponService.resolveCoupon(null)).thenReturn(null);
        when(campaignService.resolveCampaignCredit(eq(menteeId), any(BookingCheckoutSnapshot.class), eq(72_000)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());
        when(creditLedgerService.reserveCredit(eq(menteeId), eq(72_000), eq(LedgerSourceType.PAYMENT_ORDER), any(), any(), any()))
                .thenReturn(List.of(CreditLedgerEntry.builder()
                        .amountScoin(72_000)
                        .originType(CreditOriginType.MANUAL)
                        .build()));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.checkout(menteeId, new PaymentCheckoutRequest(bookingId, null));

        assertEquals(PaymentOrderStatus.PAID, response.status());
        assertEquals(0, response.remainingPayableScoin());
        assertNull(response.paymentLink());
        verify(creditLedgerService).reserveCredit(eq(menteeId), eq(72_000), eq(LedgerSourceType.PAYMENT_ORDER), any(), any(), any());
        verify(creditLedgerService).consumeReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), any(), any());
        // PayOsGateway must NOT be called for credit-covered checkout
        verify(payOsGateway, never()).createPaymentLink(any());
    }

    @Test
    void checkout_providerPaymentRequired_shouldLockOrderBeforeAttemptWhenCompletingPaymentLink() {
        List<String> acquiredLocks = new ArrayList<>();
        AtomicReference<PaymentOrder> savedOrder = new AtomicReference<>();
        AtomicReference<PaymentAttempt> savedAttempt = new AtomicReference<>();

        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId))
                .thenReturn(Optional.empty());
        when(couponService.resolveCoupon(null)).thenReturn(null);
        when(campaignService.resolveCampaignCredit(eq(menteeId), any(BookingCheckoutSnapshot.class), eq(72_000)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());
        when(creditLedgerService.getAvailableBalanceByOrigin(menteeId))
                .thenReturn(new java.util.EnumMap<>(CreditOriginType.class));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> {
            PaymentOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(UUID.randomUUID());
            }
            savedOrder.set(order);
            return order;
        });
        when(paymentAttemptRepository.countByPaymentOrderId(any())).thenReturn(0L);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            if (attempt.getId() == null) {
                attempt.setId(UUID.randomUUID());
            }
            savedAttempt.set(attempt);
            return attempt;
        });
        when(payOsGateway.createPaymentLink(any())).thenReturn(new PayOsGateway.CreatePaymentLinkResult(
                "123456789", "pl-123", "PENDING", "https://pay.example/123", LocalDateTime.now().plusMinutes(15)));
        when(paymentOrderRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            acquiredLocks.add("payment-order");
            return Optional.of(savedOrder.get());
        });
        when(paymentAttemptRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            acquiredLocks.add("payment-attempt");
            return Optional.of(savedAttempt.get());
        });

        PaymentCheckoutResponse response = paymentOrderService.checkout(
                menteeId, new PaymentCheckoutRequest(bookingId, null));

        assertEquals("https://pay.example/123", response.paymentLink());
        assertEquals(List.of("payment-order", "payment-attempt"), acquiredLocks);
    }

    @Test
    void checkout_afterFailedProviderAttempt_shouldCreateRetryWithoutChangingBookingState() {
        PaymentOrder existingOrder = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-RETRY-1")
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(72_000)
                .remainingPayableScoin(72_000)
                .status(PaymentOrderStatus.FAILED)
                .build();
        PaymentAttempt previousAttempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(existingOrder.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.FAILED)
                .build();
        AtomicReference<PaymentAttempt> retryAttempt = new AtomicReference<>();

        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId))
                .thenReturn(Optional.of(existingOrder));
        when(paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(existingOrder.getId()))
                .thenReturn(Optional.of(previousAttempt));
        when(couponService.resolveCoupon(null)).thenReturn(null);
        when(campaignService.resolveCampaignCredit(eq(menteeId), any(BookingCheckoutSnapshot.class), eq(72_000)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());
        when(creditLedgerService.getAvailableBalanceByOrigin(menteeId))
                .thenReturn(new java.util.EnumMap<>(CreditOriginType.class));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentAttemptRepository.countByPaymentOrderId(existingOrder.getId())).thenReturn(1L);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            if (attempt.getId() == null) {
                attempt.setId(UUID.randomUUID());
            }
            retryAttempt.set(attempt);
            return attempt;
        });
        when(payOsGateway.createPaymentLink(any())).thenReturn(new PayOsGateway.CreatePaymentLinkResult(
                "987654321", "pl-retry", "PENDING", "https://pay.example/retry", LocalDateTime.now().plusMinutes(15)));
        when(paymentOrderRepository.findByIdForUpdate(existingOrder.getId())).thenReturn(Optional.of(existingOrder));
        when(paymentAttemptRepository.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.of(retryAttempt.get()));

        PaymentCheckoutResponse response = paymentOrderService.checkout(
                menteeId, new PaymentCheckoutRequest(bookingId, null));

        assertEquals(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT, response.status());
        assertEquals(2, retryAttempt.get().getAttemptNo());
        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, booking.getStatus());
        assertEquals("https://pay.example/retry", response.paymentLink());
    }

    @Test
    void handleMenteeCancellation_awaitingPayment_shouldRollbackReservedCreditAndVoidCoupon() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-CANCEL-1")
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();

        when(paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMenteeCancellation(booking.getId(), false);

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
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.PAID)
                .build();

        when(paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMenteeCancellation(booking.getId(), true);

        assertNotNull(order.getCancelledAt());
        verify(settlementService).handlePaidBookingCancelledByMentee(any(BookingCancellationContext.class), eq(order), eq(true));
    }

    @Test
    void handleMentorCancellation_awaitingPayment_shouldRollbackReservedCreditAndVoidCoupon() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-MENTOR-CANCEL-1")
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();

        when(paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMentorCancellation(booking.getId());

        assertEquals(PaymentOrderStatus.CANCELLED, order.getStatus());
        verify(creditLedgerService).releaseReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
        verify(couponService).voidRedemption(order.getId());
        verifyNoInteractions(settlementService);
    }

    @Test
    void expireAwaitingPayment_shouldMarkPaymentOrderExpiredAndReleaseReservations() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-EXPIRE-1")
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();

        when(paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.expireAwaitingPayment(booking.getId());

        assertEquals(PaymentOrderStatus.EXPIRED, order.getStatus());
        assertNotNull(order.getFailedAt());
        verify(creditLedgerService).releaseReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
        verify(couponService).voidRedemption(order.getId());
    }

    @Test
    void handleMentorCancellation_paid_shouldDelegateFullRefundToSettlement() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-MENTOR-CANCEL-2")
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .status(PaymentOrderStatus.PAID)
                .build();

        when(paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentOrderService.handleMentorCancellation(booking.getId());

        assertNotNull(order.getCancelledAt());
        verify(settlementService).handlePaidBookingCancelledByMentor(any(BookingCancellationContext.class), eq(order));
    }

    // ─── Webhook: valid signature (gateway returns success) ──────────────────────

    @Test
    void handleWebhook_paid_gatewayVerifiesOk_shouldConsumeReservedCreditAndMarkSucceeded() {
        Long orderCode = 123456789L;
        PaymentWebhookRequest webhookRequest = buildWebhookRequest(orderCode, "valid-hmac-signature");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-TEST")
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
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
        // Optimistic (non-locking) read before entering the transaction
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findTargetIdById(order.getId())).thenReturn(Optional.of(bookingId));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
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

    // ─── Webhook: signed terminal provider status ─────────────────────────────────

    @Test
    void handleWebhook_providerCancelled_shouldPersistTerminalStateAndAcknowledge() {
        Long orderCode = 111L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .providerOrderCode(String.valueOf(orderCode))
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .remainingPayableScoin(100)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .build();

        // A false request.success means payment failed/cancelled, not that its signature is invalid.
        PayOsGateway.VerifiedWebhook notPaid = new PayOsGateway.VerifiedWebhook(
                String.valueOf(orderCode), "pl-111", "evt-111", "txn-111",
                "CANCELLED",
                false,
                (java.time.Instant) null,
                0L
        );
        when(payOsGateway.verifyWebhook(request)).thenReturn(notPaid);
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findTargetIdById(order.getId())).thenReturn(Optional.of(bookingId));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.handleWebhook(request);

        assertEquals(PaymentOrderStatus.CANCELLED, response.status());
        assertEquals(PaymentAttemptStatus.CANCELLED, attempt.getStatus());
        verify(creditLedgerService, never()).consumeReservedCredit(any(), any(), any(), any());
        verify(paymentOrderRepository).save(order);
    }

    @Test
    void handleWebhook_lateFailureFromOlderRetry_shouldNotCancelCurrentPaymentOrder() {
        Long oldOrderCode = 111L;
        Long currentOrderCode = 222L;
        PaymentWebhookRequest request = buildWebhookRequest(oldOrderCode, "valid-sig");
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .providerOrderCode(String.valueOf(currentOrderCode))
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .remainingPayableScoin(100)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt oldAttempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .providerOrderCode(String.valueOf(oldOrderCode))
                .build();

        PayOsGateway.VerifiedWebhook failed = new PayOsGateway.VerifiedWebhook(
                String.valueOf(oldOrderCode), "pl-old", "evt-old", "txn-old",
                "FAILED", false, (java.time.Instant) null, 0L);
        when(payOsGateway.verifyWebhook(request)).thenReturn(failed);
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(oldOrderCode))).thenReturn(Optional.of(oldAttempt));
        when(paymentOrderRepository.findTargetIdById(order.getId())).thenReturn(Optional.of(bookingId));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdForUpdate(oldAttempt.getId())).thenReturn(Optional.of(oldAttempt));
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentOrderRepository.existsByProviderEventId("evt-old")).thenReturn(false);
        when(paymentAttemptRepository.existsByProviderEventId("evt-old")).thenReturn(false);

        paymentOrderService.handleWebhook(request);

        assertEquals(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT, order.getStatus());
        assertEquals(PaymentAttemptStatus.FAILED, oldAttempt.getStatus());
        verify(creditLedgerService, never()).releaseReservedCredit(any(), any(), any(), any());
        verify(couponService, never()).voidRedemption(any());
        verify(paymentOrderRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    void handleWebhook_failureAfterOrderAlreadyPaid_shouldKeepPaidFinancialState() {
        Long orderCode = 333L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .providerOrderCode(String.valueOf(orderCode))
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .remainingPayableScoin(0)
                .status(PaymentOrderStatus.PAID)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .providerOrderCode(String.valueOf(orderCode))
                .build();
        PayOsGateway.VerifiedWebhook failed = new PayOsGateway.VerifiedWebhook(
                String.valueOf(orderCode), "pl-paid", "evt-failed", "txn-failed",
                "FAILED", false, (java.time.Instant) null, 0L);
        when(payOsGateway.verifyWebhook(request)).thenReturn(failed);
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findTargetIdById(order.getId())).thenReturn(Optional.of(bookingId));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentOrderRepository.existsByProviderEventId("evt-failed")).thenReturn(false);
        when(paymentAttemptRepository.existsByProviderEventId("evt-failed")).thenReturn(false);

        paymentOrderService.handleWebhook(request);

        assertEquals(PaymentOrderStatus.PAID, order.getStatus());
        assertEquals(PaymentAttemptStatus.FAILED, attempt.getStatus());
        verify(creditLedgerService, never()).releaseReservedCredit(any(), any(), any(), any());
        verify(paymentOrderRepository, never()).save(any(PaymentOrder.class));
    }

    // ─── Webhook idempotency: duplicate eventId ───────────────────────────────────

    @Test
    void handleWebhook_duplicateEventId_shouldReturnEarlyWithoutReprocessing() {
        Long orderCode = 222L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
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
        // Optimistic read before entering the transaction
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findTargetIdById(order.getId())).thenReturn(Optional.of(bookingId));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
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
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
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
        // Optimistic read before entering the transaction
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        paymentOrderService.handleWebhook(request);

        paymentOrderService.handleWebhook(request);

        verify(creditLedgerService, never()).consumeReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), any(), any());
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void handleWebhook_paidAmountLowerThanExpected_shouldRejectWithoutFinalizing() {
        Long orderCode = 445L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .providerOrderCode(String.valueOf(orderCode))
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100_000)
                .remainingPayableScoin(100_000)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .build();

        PayOsGateway.VerifiedWebhook underpaid = new PayOsGateway.VerifiedWebhook(
                String.valueOf(orderCode),
                "pl-" + orderCode,
                "evt-" + orderCode,
                "txn-underpaid",
                "PAID",
                true,
                LocalDateTime.now(),
                90_000L
        );
        when(payOsGateway.verifyWebhook(request)).thenReturn(underpaid);
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findTargetIdById(order.getId())).thenReturn(Optional.of(bookingId));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.existsByProviderEventId("evt-" + orderCode)).thenReturn(false);
        when(paymentAttemptRepository.existsByProviderEventId("evt-" + orderCode)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class, () -> paymentOrderService.handleWebhook(request));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(creditLedgerService, never()).consumeReservedCredit(any(), any(), any(), any());
        verify(creditLedgerService, never()).issueCredit(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(paymentOrderRepository, never()).save(any());
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void handleWebhook_orderAlreadyPaid_attemptNotPaid_shouldIssueSurplusCredit() {
        Long orderCode = 444L;
        PaymentWebhookRequest request = buildWebhookRequest(orderCode, "valid-sig");

        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .orderCode("PAY-SURPLUS")
                .providerOrderCode(String.valueOf(111L)) // Original successful code
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100_000)
                .remainingPayableScoin(0)
                .status(PaymentOrderStatus.PAID) // already finalized
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(2)
                .status(PaymentAttemptStatus.REDIRECTED) // NOT PAID yet
                .build();

        when(payOsGateway.verifyWebhook(request)).thenReturn(verifiedWebhook(String.valueOf(orderCode), "txn-surplus"));
        when(paymentAttemptRepository.findByProviderOrderCode(String.valueOf(orderCode))).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findTargetIdById(order.getId())).thenReturn(Optional.of(bookingId));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.existsByProviderEventId(any())).thenReturn(false);
        when(paymentAttemptRepository.existsByProviderEventId(any())).thenReturn(false);
        when(creditLedgerService.hasIssuedCreditForSource(LedgerSourceType.PAYMENT_ATTEMPT, attempt.getId())).thenReturn(false);

        paymentOrderService.handleWebhook(request);

        verify(creditLedgerService).issueCredit(
                eq(menteeId),
                eq(CreditOriginType.PAYMENT_SURPLUS),
                eq(LedgerSourceType.PAYMENT_ATTEMPT),
                eq(attempt.getId()),
                eq(100_000), // verified.amount() from verifiedWebhook
                any()
        );
        assertEquals(PaymentAttemptStatus.SUCCEEDED_SURPLUS, attempt.getStatus());
    }

    @Test
    void manualSyncByBookingId_providerStatusPaid_shouldFinalizeAsWebhookFallback() {
        List<String> acquiredLocks = new ArrayList<>();
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .providerOrderCode("123456789")
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .remainingPayableScoin(50)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .providerOrderCode("123456789")
                .build();

        when(paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(order.getId())).thenReturn(Optional.of(attempt));
        when(payOsGateway.getPaymentLink(123456789L))
                .thenReturn(new PayOsGateway.PaymentLinkDetails("pl-123", "PAID", LocalDateTime.now().minusMinutes(5), null));
        when(bookingPaymentSettlementPort.findPaymentSnapshotForUpdate(bookingId)).thenAnswer(invocation -> {
            acquiredLocks.add("booking");
            return Optional.of(new BookingPaymentSnapshot(
                    bookingId, menteeId, mentorId, null, 100, false,
                    "ACCEPTED_AWAITING_PAYMENT", Instant.now().minusSeconds(60),
                    Instant.now().plusSeconds(3600), Instant.now().plusSeconds(900)));
        });
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenAnswer(invocation -> {
            acquiredLocks.add("payment-order");
            return Optional.of(order);
        });
        when(paymentAttemptRepository.findByIdForUpdate(attempt.getId())).thenAnswer(invocation -> {
            acquiredLocks.add("payment-attempt");
            return Optional.of(attempt);
        });
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.synchronizeProviderStatus(
                menteeId,
                PaymentTargetType.BOOKING,
                bookingId
        );

        assertEquals(PaymentOrderStatus.PAID, response.status());
        assertEquals(PaymentAttemptStatus.SUCCEEDED, attempt.getStatus());
        assertEquals(List.of("booking", "payment-order", "payment-attempt"), acquiredLocks);
        verify(bookingPaymentSettlementPort).confirmPayment(eq(bookingId), any(Instant.class));
        verify(creditLedgerService).issueCredit(eq(menteeId), eq(CreditOriginType.MANUAL), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), eq(50), any());
        verify(creditLedgerService).consumeReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
    }

    @Test
    void getByBookingId_providerStatusSuccess_shouldOnlyReadDatabase() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(PaymentTargetType.BOOKING).targetId(bookingId)
                .providerOrderCode("987654321")
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .remainingPayableScoin(25)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .providerOrderCode("987654321")
                .build();

        when(paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(order.getId())).thenReturn(Optional.of(attempt));
        PaymentCheckoutResponse response = paymentOrderService.getByTarget(menteeId, com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType.BOOKING, bookingId);

        assertEquals(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT, response.status());
        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, booking.getStatus());
        verifyNoInteractions(payOsGateway);
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

        Booking freeBooking = Booking.builder()
                .id(UUID.randomUUID())
                .menteeUserId(menteeUser.getId())
                .mentorUserId(mentorUser.getId())
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

    @Test
    void generateProviderOrderCode_shouldStayWithinPayOsSafeIntegerRangeAndRemainUnique() throws Exception {
        Method method = PaymentOrderService.class.getDeclaredMethod("generateProviderOrderCode", UUID.class, int.class);
        method.setAccessible(true);

        Set<Long> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long value = (long) method.invoke(paymentOrderService, UUID.randomUUID(), 1);
            assertTrue(value > 0);
            assertTrue(value <= 9_007_199_254_740_991L);
            assertTrue(generated.add(value), "Duplicate providerOrderCode generated: " + value);
        }
    }

    @Test
    void checkout_withMenteeSurchargeAndMentorCommission_shouldApplyRatesCorrectly() {
        PaymentProperties props = new PaymentProperties();
        props.getPayos().setReturnUrl("https://skillswap.asia/payment/return");
        props.getPayos().setCancelUrl("https://skillswap.asia/payment/cancel");
        props.setMenteeSurchargeBps(1000); // 10%
        props.setMentorCommissionBps(1000); // 10%

        PaymentOrderService customService = new PaymentOrderService(
                new BookingQueryPortImpl(bookingRepository),
                userQueryPort,
                bookingPaymentSettlementPort,
                paymentOrderRepository,
                paymentAttemptRepository,
                couponService,
                creditLedgerService,
                campaignService,
                props,
                new PaymentGatewayProviderFactory(List.of(new PayOsPaymentGatewayProvider(payOsGateway))),
                settlementService,
                eventPublisher,
                internalTelemetryService,
                transactionTemplate
        );

        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId)).thenReturn(Optional.empty());
        when(couponService.resolveCoupon(null)).thenReturn(null);
        // With surcharge-added price = 79,200
        when(campaignService.resolveCampaignCredit(eq(menteeId), any(BookingCheckoutSnapshot.class), eq(79_200)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());
        when(creditLedgerService.reserveCredit(eq(menteeId), eq(79_200), eq(LedgerSourceType.PAYMENT_ORDER), any(), any(), any()))
                .thenReturn(List.of(CreditLedgerEntry.builder()
                        .amountScoin(79_200)
                        .originType(CreditOriginType.MANUAL)
                        .build()));
        
        ArgumentCaptor<PaymentOrder> orderCaptor = ArgumentCaptor.forClass(PaymentOrder.class);
        when(paymentOrderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));


        PaymentCheckoutResponse response = customService.checkout(menteeId, new PaymentCheckoutRequest(bookingId, null));

        assertNotNull(response);
        PaymentOrder capturedOrder = orderCaptor.getValue();
        assertEquals(79_200, capturedOrder.getGrossScoin());
        assertEquals(64_800, capturedOrder.getMentorNetScoin());
        assertEquals(14_400, capturedOrder.getCommissionScoin());
        assertEquals(2000, capturedOrder.getCommissionRateBps()); // 10% + 10%
    }
}
