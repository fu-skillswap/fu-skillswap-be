package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingCancellationContext;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProvider;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentWebhookServiceTest {

    @Test
    void terminalWebhook_writesItsUtcAndLegacyTimestampFromTheSameInstant() {
        Instant receivedAtUtc = Instant.parse("2026-09-01T03:00:00Z");
        PaymentWebhookService service = newService(mock(BookingPaymentSettlementPort.class), mock(SettlementService.class));
        service.setTimeProvider(TimeProvider.fixedUtc(receivedAtUtc));

        PaymentOrder order = PaymentOrder.builder().build();
        PaymentAttempt attempt = PaymentAttempt.builder().build();
        PaymentGatewayProvider.VerifiedWebhook webhook = new PaymentGatewayProvider.VerifiedWebhook(
                "123", "link-1", "event-1", "transaction-1", "EXPIRED", false, (Instant) null, 0L);

        service.applyTerminalWebhook(order, attempt, webhook, "event-1");

        assertEquals(PaymentOrderStatus.EXPIRED, order.getStatus());
        assertEquals(receivedAtUtc, order.getFailedAtUtc());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0), order.getFailedAt());
        assertEquals(PaymentAttemptStatus.EXPIRED, attempt.getStatus());
    }

    @Test
    void paidAfterBookingDeadline_expiresBookingAndCompensatesInsteadOfConfirmingSession() {
        Instant deadline = Instant.parse("2026-09-01T03:00:00Z");
        PaymentProperties properties = new PaymentProperties();
        BookingPaymentSettlementPort bookingPaymentSettlementPort = mock(BookingPaymentSettlementPort.class);
        SettlementService settlementService = mock(SettlementService.class);
        PaymentWebhookService service = newService(bookingPaymentSettlementPort, settlementService);
        service.setTimeProvider(TimeProvider.fixed(deadline.plusSeconds(1), TimeProvider.BUSINESS_ZONE));

        UUID bookingId = UUID.randomUUID();
        BookingPaymentSnapshot booking = new BookingPaymentSnapshot(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), null, 100, false,
                "ACCEPTED_AWAITING_PAYMENT", deadline.minus(Duration.ofMinutes(240)),
                deadline.plus(Duration.ofHours(2)), deadline);
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .paidAtUtc(deadline)
                .build();
        when(bookingPaymentSettlementPort.findCancellationContext(bookingId))
                .thenReturn(java.util.Optional.of(new BookingCancellationContext(
                        bookingId, booking.payerUserId(), booking.mentorUserId(), "EXPIRED", null,
                        null, booking.selectedStartAtUtc(), deadline, false, true)));

        service.finalizePaidBooking(order, booking);

        verify(bookingPaymentSettlementPort).expirePayment(
                bookingId, deadline, "Yêu cầu đặt lịch đã hết hạn trước khi cổng thanh toán xác nhận giao dịch.");
        verify(settlementService).handlePaidBookingCancelledByMentor(any(), eq(order));
    }

    @Test
    void successfulPayment_usesBookingLifecycleContractToConfirmAndCreateSideEffects() {
        BookingPaymentSettlementPort bookingPaymentSettlementPort = mock(BookingPaymentSettlementPort.class);
        PaymentWebhookService service = newService(bookingPaymentSettlementPort, mock(SettlementService.class));
        UUID bookingId = UUID.randomUUID();
        BookingPaymentSnapshot booking = new BookingPaymentSnapshot(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), null, 100, false,
                "ACCEPTED_AWAITING_PAYMENT", Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T06:00:00Z"), Instant.parse("2026-09-01T03:00:00Z"));
        PaymentOrder order = PaymentOrder.builder().id(UUID.randomUUID()).paidAtUtc(Instant.parse("2026-09-01T02:00:00Z")).build();

        service.finalizePaidBooking(order, booking);

        verify(bookingPaymentSettlementPort).confirmPayment(eq(bookingId), any(Instant.class));
        verify(bookingPaymentSettlementPort, never()).ensurePaidSideEffects(any(UUID.class));
    }

    @Test
    void missingBookingContract_shouldFailExplicitly() {
        PaymentWebhookService service = newService(mock(BookingPaymentSettlementPort.class), mock(SettlementService.class));

        // This is a programmer/precondition error: the caller must supply its already-locked
        // immutable booking snapshot, so the method intentionally uses IllegalArgumentException.
        assertThrows(IllegalArgumentException.class, () -> service.finalizePaidBooking(
                PaymentOrder.builder().build(), null));
    }

    @Test
    void invalidWebhookRequest_shouldBeRejectedByProviderValidation() {
        PaymentGatewayProviderFactory factory = mock(PaymentGatewayProviderFactory.class);
        PaymentGatewayProvider provider = mock(PaymentGatewayProvider.class);
        PaymentWebhookService service = newService(mock(BookingPaymentSettlementPort.class), mock(SettlementService.class), factory);
        PaymentWebhookRequest request = mock(PaymentWebhookRequest.class);
        PaymentWebhookRequest.PaymentWebhookDataRequest data = mock(PaymentWebhookRequest.PaymentWebhookDataRequest.class);
        when(request.data()).thenReturn(data);
        when(data.orderCode()).thenReturn(123L);
        when(factory.getProvider(PaymentProvider.PAYOS)).thenReturn(provider);
        when(provider.verifyWebhook(request)).thenThrow(new BaseException(
                com.fptu.exe.skillswap.shared.exception.ErrorCode.UNAUTHORIZED,
                "Webhook PayOS không hợp lệ hoặc sai chữ ký"));

        assertThrows(BaseException.class, () -> service.handleWebhook(request));
        verify(provider).verifyWebhook(request);
    }

    @Test
    void reconciliation_stopsBeforeStartingAnotherProviderCallAfterItsTimeBudget() throws Exception {
        PaymentProperties properties = new PaymentProperties();
        properties.setReconciliationMaxOrdersPerRun(2);
        properties.setReconciliationMaxDurationSeconds(1);
        PaymentOrder first = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetId(UUID.randomUUID())
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentOrder second = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetId(UUID.randomUUID())
                .status(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT)
                .build();
        PaymentOrderRepository orderRepository = mock(PaymentOrderRepository.class);
        when(orderRepository.findTop50ByStatusInAndUpdatedAtUtcBeforeOrderByUpdatedAtUtcAsc(
                any(), any(Instant.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        PaymentWebhookService service = spy(new PaymentWebhookService(
                orderRepository,
                mock(PaymentAttemptRepository.class),
                mock(CreditLedgerService.class),
                mock(CouponService.class),
                mock(SettlementService.class),
                mock(BookingPaymentSettlementPort.class),
                mock(PaymentGatewayProviderFactory.class),
                mock(PaymentLifecycleService.class),
                mock(PaymentResponseMapper.class),
                mock(InternalTelemetryService.class),
                mock(TransactionTemplate.class),
                properties
        ));
        doAnswer(invocation -> {
            Thread.sleep(1_050);
            return null;
        }).when(service).synchronizeProviderStatusForBooking(any(UUID.class));

        service.reconcileStaleProviderPayments();

        verify(service, times(1)).synchronizeProviderStatusForBooking(any(UUID.class));
        verify(orderRepository).findTop50ByStatusInAndUpdatedAtUtcBeforeOrderByUpdatedAtUtcAsc(
                any(), any(Instant.class), any(LocalDateTime.class), any(Pageable.class));
    }

    private PaymentWebhookService newService(BookingPaymentSettlementPort bookingPaymentSettlementPort,
                                             SettlementService settlementService) {
        return newService(bookingPaymentSettlementPort, settlementService, mock(PaymentGatewayProviderFactory.class));
    }

    private PaymentWebhookService newService(BookingPaymentSettlementPort bookingPaymentSettlementPort,
                                             SettlementService settlementService,
                                             PaymentGatewayProviderFactory paymentGatewayProviderFactory) {
        return new PaymentWebhookService(
                mock(PaymentOrderRepository.class),
                mock(PaymentAttemptRepository.class),
                mock(CreditLedgerService.class),
                mock(CouponService.class),
                settlementService,
                bookingPaymentSettlementPort,
                paymentGatewayProviderFactory,
                mock(PaymentLifecycleService.class),
                mock(PaymentResponseMapper.class),
                mock(InternalTelemetryService.class),
                mock(TransactionTemplate.class),
                new PaymentProperties());
    }
}
