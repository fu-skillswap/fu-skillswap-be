package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProvider;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCheckoutErrorContractTest {

    @Mock private BookingCheckoutQueryPort bookingCheckoutQueryPort;
    @Mock private UserQueryPort userQueryPort;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentGatewayProviderFactory paymentGatewayProviderFactory;
    @Mock private PaymentWebhookService paymentWebhookService;
    @Mock private PaymentLifecycleService paymentLifecycleService;
    @Mock private PaymentOrderCodeGenerator paymentOrderCodeGenerator;
    @Mock private PaymentResponseMapper paymentResponseMapper;
    @Mock private InternalTelemetryService internalTelemetryService;
    @Mock private TransactionTemplate transactionTemplate;

    @Test
    void checkoutCompletionFailureUsesStableCheckoutFailureCode() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingCheckoutSnapshot booking = new BookingCheckoutSnapshot(
                bookingId, userId, UUID.randomUUID(), UUID.randomUUID(), false,
                20_000, 30, "Java", "ACCEPTED_AWAITING_PAYMENT",
                Instant.parse("2026-09-02T03:00:00Z"));
        PaymentGatewayProvider provider = mock(PaymentGatewayProvider.class);

        AtomicInteger transactionNumber = new AtomicInteger();
        doAnswer(invocation -> {
            if (transactionNumber.incrementAndGet() == 1) {
                return ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            }
            throw new IllegalStateException("checkout persistence detail");
        })
                .when(transactionTemplate).execute(any(TransactionCallback.class));
        when(bookingCheckoutQueryPort.findCheckoutSnapshotForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(paymentOrderRepository.findByTargetTypeAndTargetId(any(), eq(bookingId))).thenReturn(Optional.empty());
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentAttemptRepository.countByPaymentOrderId(any())).thenReturn(0L);
        when(paymentAttemptRepository.save(any(PaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentOrderCodeGenerator.generateOrderCode(any())).thenReturn("PAY-TEST");
        when(paymentOrderCodeGenerator.generateProviderOrderCode(any(), eq(1))).thenReturn(123L);
        when(paymentGatewayProviderFactory.getProvider(PaymentProvider.PAYOS)).thenReturn(provider);
        when(provider.createPaymentLink(any())).thenReturn(new PaymentGatewayProvider.CreatePaymentLinkResult(
                "123", "link-123", "SUCCESS", "https://pay.example/123",
                Instant.parse("2026-09-01T04:00:00Z")));

        PaymentProperties properties = new PaymentProperties();
        properties.setPaymentLinkExpiryMinutes(30);
        PaymentCheckoutService service = new PaymentCheckoutService(
                bookingCheckoutQueryPort, userQueryPort, paymentOrderRepository, paymentAttemptRepository,
                null, null, null, properties, paymentGatewayProviderFactory, paymentWebhookService,
                paymentLifecycleService, paymentOrderCodeGenerator, paymentResponseMapper,
                internalTelemetryService, transactionTemplate);
        service.setTimeProvider(com.fptu.exe.skillswap.shared.time.TimeProvider.fixed(
                Instant.parse("2026-09-01T03:00:00Z"), com.fptu.exe.skillswap.shared.time.TimeProvider.BUSINESS_ZONE));

        BaseException exception = assertThrows(BaseException.class,
                () -> service.checkout(userId, new com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest(
                        bookingId, null)));

        assertEquals(ErrorCode.PAYMENT_CHECKOUT_FAILED, exception.getErrorCode());
        assertEquals("Không thể tạo thanh toán", exception.getMessage());
    }
}
