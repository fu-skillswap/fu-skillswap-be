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
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    @Mock
    private BookingRepository bookingRepository;
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

    private PaymentOrderService paymentOrderService;
    private PaymentProperties paymentProperties;
    private UUID menteeId;
    private UUID mentorId;
    private UUID bookingId;
    private Booking booking;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();
        paymentProperties.getPayos().setClientId("client-id");
        paymentProperties.getPayos().setApiKey("api-key");
        paymentProperties.getPayos().setChecksumKey("checksum-key");
        paymentProperties.getPayos().setReturnUrl("https://skillswap.asia/payment/return");
        paymentProperties.getPayos().setCancelUrl("https://skillswap.asia/payment/cancel");

        paymentOrderService = new PaymentOrderService(
                bookingRepository,
                paymentOrderRepository,
                paymentAttemptRepository,
                couponService,
                creditLedgerService,
                campaignService,
                paymentProperties,
                payOsGateway
        );

        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        User mentee = new User();
        mentee.setId(menteeId);
        mentee.setFullName("Nguyen Van An");
        mentee.setEmail("an@example.com");

        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .build();

        booking = Booking.builder()
                .id(bookingId)
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .status(BookingStatus.ACCEPTED)
                .serviceTitleSnapshot("Spring Boot mentoring")
                .servicePriceScoinSnapshot(100)
                .build();
    }

    @Test
    void checkout_creditFullyCovers_shouldCompleteInternallyWithoutCheckoutUrl() {
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
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentAttemptRepository.countByPaymentOrderId(any())).thenReturn(0L);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.checkout(menteeId, new PaymentCheckoutRequest(bookingId, null));

        assertEquals(PaymentOrderStatus.PAID, response.status());
        assertEquals(0, response.remainingPayableScoin());
        assertNull(response.checkoutUrl());
        verify(payOsGateway, never()).createPaymentLink(any());
        verify(creditLedgerService).consumeReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), any(), any());
    }

    @Test
    void checkout_remainingPayable_shouldCreateRealPayOsAttempt() {
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(couponService.resolveCoupon(null)).thenReturn(null);
        when(campaignService.resolveCampaignCredit(eq(menteeId), eq(booking), eq(100)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());
        when(creditLedgerService.reserveCredit(eq(menteeId), eq(100), eq(LedgerSourceType.PAYMENT_ORDER), any(), any(), any()))
                .thenReturn(List.of());
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentAttemptRepository.countByPaymentOrderId(any())).thenReturn(0L);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payOsGateway.createPaymentLink(any())).thenReturn(new PayOsGateway.CreatePaymentLinkResult(
                "123456789",
                "plink_123",
                "PENDING",
                "https://pay.payos.vn/link",
                LocalDateTime.now().plusMinutes(30)
        ));

        PaymentCheckoutResponse response = paymentOrderService.checkout(menteeId, new PaymentCheckoutRequest(bookingId, null));

        assertEquals(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT, response.status());
        assertEquals("https://pay.payos.vn/link", response.checkoutUrl());
        assertEquals("123456789", response.providerOrderCode());
        assertEquals("plink_123", response.providerPaymentLinkId());

        ArgumentCaptor<PayOsGateway.CreatePaymentLinkCommand> commandCaptor =
                ArgumentCaptor.forClass(PayOsGateway.CreatePaymentLinkCommand.class);
        verify(payOsGateway).createPaymentLink(commandCaptor.capture());
        assertEquals(100L, commandCaptor.getValue().amountVnd());
    }

    @Test
    void handleWebhook_validPaidWebhook_shouldConsumeReservedCreditAndMarkSucceeded() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-TEST")
                .bookingId(bookingId)
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
                .providerOrderCode("123456789")
                .build();

        when(payOsGateway.verifyWebhook(any())).thenReturn(new PayOsGateway.VerifiedWebhook(
                "123456789",
                "plink_123",
                "ref_123",
                "ref_123",
                "00",
                true,
                LocalDateTime.now()
        ));
        when(paymentAttemptRepository.findByProviderOrderCodeForUpdate("123456789")).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.existsByProviderEventId("ref_123")).thenReturn(false);
        when(paymentAttemptRepository.existsByProviderEventId("ref_123")).thenReturn(false);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.handleWebhook(sampleWebhookRequest(123456789L));

        assertEquals(PaymentOrderStatus.PAID, response.status());
        verify(creditLedgerService).consumeReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
        verify(couponService).markRedeemed(order.getId());

        ArgumentCaptor<PaymentAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentAttemptRepository).save(attemptCaptor.capture());
        assertEquals(PaymentAttemptStatus.SUCCEEDED, attemptCaptor.getValue().getStatus());
        assertEquals("PAID", attemptCaptor.getValue().getProviderStatus());
    }

    @Test
    void handleWebhook_duplicateProviderEvent_shouldReturnWithoutReprocessing() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .orderCode("PAY-DUP")
                .bookingId(bookingId)
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
                .providerOrderCode("123456789")
                .build();

        when(payOsGateway.verifyWebhook(any())).thenReturn(new PayOsGateway.VerifiedWebhook(
                "123456789",
                "plink_123",
                "ref_dup",
                "ref_dup",
                "00",
                true,
                LocalDateTime.now()
        ));
        when(paymentAttemptRepository.findByProviderOrderCodeForUpdate("123456789")).thenReturn(Optional.of(attempt));
        when(paymentOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.existsByProviderEventId("ref_dup")).thenReturn(true);

        PaymentCheckoutResponse response = paymentOrderService.handleWebhook(sampleWebhookRequest(123456789L));

        assertEquals(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT, response.status());
        verify(creditLedgerService, never()).consumeReservedCredit(any(), any(), any(), any());
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void getByBookingId_providerExpired_shouldSyncFinalStatusAndReleaseReserve() {
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .orderCode("PAY-EXPIRED")
                .grossScoin(100)
                .remainingPayableScoin(100)
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentAttempt attempt = PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(order.getId())
                .attemptNo(1)
                .status(PaymentAttemptStatus.REDIRECTED)
                .providerOrderCode("123456789")
                .build();

        when(paymentOrderRepository.findByBookingId(bookingId)).thenReturn(Optional.of(order));
        when(paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(order.getId())).thenReturn(Optional.of(attempt));
        when(payOsGateway.getPaymentLink(123456789L)).thenReturn(new PayOsGateway.PaymentLinkDetails(
                "plink_123",
                "EXPIRED",
                LocalDateTime.now().minusMinutes(30),
                null
        ));
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentCheckoutResponse response = paymentOrderService.getByBookingId(menteeId, bookingId);

        assertEquals(PaymentOrderStatus.EXPIRED, response.status());
        verify(creditLedgerService).releaseReservedCredit(eq(menteeId), eq(LedgerSourceType.PAYMENT_ORDER), eq(order.getId()), any());
        verify(couponService).voidRedemption(order.getId());
    }

    @Test
    void handleWebhook_notSuccessful_shouldThrowBadRequest() {
        when(payOsGateway.verifyWebhook(any())).thenReturn(new PayOsGateway.VerifiedWebhook(
                "123456789",
                "plink_123",
                "ref_fail",
                "ref_fail",
                "FAILED",
                false,
                null
        ));

        BaseException ex = assertThrows(BaseException.class,
                () -> paymentOrderService.handleWebhook(sampleWebhookRequest(123456789L)));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    private PaymentWebhookRequest sampleWebhookRequest(long orderCode) {
        return new PaymentWebhookRequest(
                "00",
                "success",
                true,
                new PaymentWebhookRequest.PaymentWebhookDataRequest(
                        orderCode,
                        100L,
                        "Thanh toan don hang",
                        "12345678",
                        "ref_123",
                        "2026-06-25 12:00:00",
                        "VND",
                        "plink_123",
                        "00",
                        "Thanh cong",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                "signed_payload"
        );
    }
}
