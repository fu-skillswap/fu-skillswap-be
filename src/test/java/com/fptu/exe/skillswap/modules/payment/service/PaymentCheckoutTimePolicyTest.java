package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PaymentCheckoutTimePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-01T03:00:00Z");

    @Test
    void providerLinkExpiry_isCappedByTheBookingPaymentDeadline() {
        PaymentCheckoutService service = serviceWithFixedTime();
        BookingCheckoutSnapshot booking = snapshotWithDeadline(NOW.plus(Duration.ofMinutes(10)));

        assertEquals(NOW.plus(Duration.ofMinutes(10)), service.resolveProviderLinkExpiryUtc(booking));
    }

    @Test
    void providerLinkExpiry_rejectsTheExactBookingDeadline() {
        PaymentCheckoutService service = serviceWithFixedTime();
        BookingCheckoutSnapshot booking = snapshotWithDeadline(NOW);

        assertThrows(BaseException.class, () -> service.resolveProviderLinkExpiryUtc(booking));
    }

    private PaymentCheckoutService serviceWithFixedTime() {
        PaymentProperties properties = new PaymentProperties();
        properties.setPaymentLinkExpiryMinutes(30);
        PaymentCheckoutService service = new PaymentCheckoutService(
                mock(BookingCheckoutQueryPort.class),
                mock(UserQueryPort.class),
                mock(PaymentOrderRepository.class),
                mock(PaymentAttemptRepository.class),
                mock(CouponService.class),
                mock(CreditLedgerService.class),
                mock(CampaignService.class),
                properties,
                mock(PaymentGatewayProviderFactory.class),
                mock(PaymentWebhookService.class),
                mock(PaymentLifecycleService.class),
                mock(PaymentOrderCodeGenerator.class),
                mock(PaymentResponseMapper.class),
                mock(InternalTelemetryService.class),
                mock(TransactionTemplate.class)
        );
        service.setTimeProvider(TimeProvider.fixed(NOW, TimeProvider.BUSINESS_ZONE));
        return service;
    }

    private BookingCheckoutSnapshot snapshotWithDeadline(Instant deadlineUtc) {
        return new BookingCheckoutSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                false, 10_000, 60, null, "ACCEPTED_AWAITING_PAYMENT", deadlineUtc);
    }
}
