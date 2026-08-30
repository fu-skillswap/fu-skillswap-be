package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.booking.service.SessionService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProvider;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentWebhookServiceTest {

    @Test
    void terminalWebhook_writesItsUtcAndLegacyTimestampFromTheSameInstant() {
        Instant receivedAtUtc = Instant.parse("2026-09-01T03:00:00Z");
        PaymentWebhookService service = new PaymentWebhookService(
                mock(PaymentOrderRepository.class),
                mock(PaymentAttemptRepository.class),
                mock(BookingQueryPort.class),
                mock(CreditLedgerService.class),
                mock(CouponService.class),
                mock(SettlementService.class),
                mock(SessionService.class),
                mock(ConversationService.class),
                mock(PaymentGatewayProviderFactory.class),
                mock(PaymentLifecycleService.class),
                mock(PaymentResponseMapper.class),
                mock(ApplicationEventPublisher.class),
                mock(InternalTelemetryService.class),
                mock(TransactionTemplate.class),
                new PaymentProperties()
        );
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
        BookingQueryPort bookingQueryPort = mock(BookingQueryPort.class);
        SettlementService settlementService = mock(SettlementService.class);
        SessionService sessionService = mock(SessionService.class);
        PaymentWebhookService service = new PaymentWebhookService(
                mock(PaymentOrderRepository.class),
                mock(PaymentAttemptRepository.class),
                bookingQueryPort,
                mock(CreditLedgerService.class),
                mock(CouponService.class),
                settlementService,
                sessionService,
                mock(ConversationService.class),
                mock(PaymentGatewayProviderFactory.class),
                mock(PaymentLifecycleService.class),
                mock(PaymentResponseMapper.class),
                mock(ApplicationEventPublisher.class),
                mock(InternalTelemetryService.class),
                mock(TransactionTemplate.class),
                properties
        );
        service.setTimeProvider(TimeProvider.fixed(deadline.plusSeconds(1), TimeProvider.BUSINESS_ZONE));

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                .acceptedAtUtc(deadline.minus(Duration.ofMinutes(240)))
                .selectedStartTimeUtc(deadline.plus(Duration.ofHours(2)))
                .build();
        PaymentOrder order = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .paidAtUtc(deadline)
                .build();

        service.finalizePaidBooking(order, booking);

        org.junit.jupiter.api.Assertions.assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(bookingQueryPort).save(booking);
        verify(settlementService).handlePaidBookingCancelledByMentor(booking, order);
        verify(sessionService, never()).createForAcceptedBooking(booking);
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
                mock(BookingQueryPort.class),
                mock(CreditLedgerService.class),
                mock(CouponService.class),
                mock(SettlementService.class),
                mock(SessionService.class),
                mock(ConversationService.class),
                mock(PaymentGatewayProviderFactory.class),
                mock(PaymentLifecycleService.class),
                mock(PaymentResponseMapper.class),
                mock(ApplicationEventPublisher.class),
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
}
