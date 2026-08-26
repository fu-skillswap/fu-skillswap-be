package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
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
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .acceptedAtUtc(NOW.minus(Duration.ofMinutes(230)))
                .selectedStartTimeUtc(NOW.plus(Duration.ofHours(3)))
                .build();

        assertEquals(NOW.plus(Duration.ofMinutes(10)), service.resolveProviderLinkExpiryUtc(booking));
    }

    @Test
    void providerLinkExpiry_rejectsTheExactBookingDeadline() {
        PaymentCheckoutService service = serviceWithFixedTime();
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .acceptedAtUtc(NOW.minus(Duration.ofMinutes(240)))
                .selectedStartTimeUtc(NOW.plus(Duration.ofHours(3)))
                .build();

        assertThrows(BaseException.class, () -> service.resolveProviderLinkExpiryUtc(booking));
    }

    private PaymentCheckoutService serviceWithFixedTime() {
        PaymentProperties properties = new PaymentProperties();
        properties.setPaymentLinkExpiryMinutes(30);
        PaymentCheckoutService service = new PaymentCheckoutService(
                mock(BookingQueryPort.class),
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
}
