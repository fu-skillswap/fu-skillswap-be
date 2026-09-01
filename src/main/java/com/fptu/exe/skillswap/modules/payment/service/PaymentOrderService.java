package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.booking.service.SessionService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutPreviewRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Facade điều phối toàn bộ các nghiệp vụ liên quan đến đơn hàng thanh toán (Payment Orders).
 * Tuân thủ Single Responsibility Principle (SRP) bằng cách ủy quyền cho các Sub-Services chuyên biệt.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PaymentOrderService {

    private final PaymentCheckoutService paymentCheckoutService;
    private final PaymentWebhookService paymentWebhookService;
    private final PaymentLifecycleService paymentLifecycleService;
    private final PaymentOrderQueryService paymentOrderQueryService;
    private final PaymentOrderCodeGenerator paymentOrderCodeGenerator;
    private final PaymentResponseMapper paymentResponseMapper;

    /**
     * Constructor hỗ trợ khởi tạo trực tiếp với Spring DI và Factory.
     */
    public PaymentOrderService(
            BookingQueryPort bookingQueryPort,
            BookingPaymentSettlementPort bookingPaymentSettlementPort,
            PaymentOrderRepository paymentOrderRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            CouponService couponService,
            CreditLedgerService creditLedgerService,
            CampaignService campaignService,
            PaymentProperties paymentProperties,
            PaymentGatewayProviderFactory paymentGatewayProviderFactory,
            SettlementService settlementService,
            SessionService sessionService,
            ConversationService conversationService,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            InternalTelemetryService internalTelemetryService,
            TransactionTemplate transactionTemplate
    ) {
        PaymentResponseMapper responseMapper = new PaymentResponseMapper(paymentProperties);
        PaymentOrderCodeGenerator codeGenerator = new PaymentOrderCodeGenerator();
        PaymentLifecycleService lifecycleService = new PaymentLifecycleService(
                paymentOrderRepository,
                creditLedgerService,
                couponService,
                settlementService,
                bookingPaymentSettlementPort
        );
        PaymentWebhookService webhookService = new PaymentWebhookService(
                paymentOrderRepository,
                paymentAttemptRepository,
                creditLedgerService,
                couponService,
                settlementService,
                bookingPaymentSettlementPort,
                paymentGatewayProviderFactory,
                lifecycleService,
                responseMapper,
                internalTelemetryService,
                transactionTemplate,
                paymentProperties
        );
        PaymentCheckoutService checkoutService = new PaymentCheckoutService(
                bookingQueryPort,
                paymentOrderRepository,
                paymentAttemptRepository,
                couponService,
                creditLedgerService,
                campaignService,
                paymentProperties,
                paymentGatewayProviderFactory,
                webhookService,
                lifecycleService,
                codeGenerator,
                responseMapper,
                internalTelemetryService,
                transactionTemplate
        );
        PaymentOrderQueryService queryService = new PaymentOrderQueryService(
                paymentOrderRepository,
                paymentAttemptRepository,
                responseMapper
        );

        this.paymentCheckoutService = checkoutService;
        this.paymentWebhookService = webhookService;
        this.paymentLifecycleService = lifecycleService;
        this.paymentOrderQueryService = queryService;
        this.paymentOrderCodeGenerator = codeGenerator;
        this.paymentResponseMapper = responseMapper;
    }

    @Autowired(required = false)
    void setBookingPricingPreviewService(BookingPricingPreviewService bookingPricingPreviewService) {
        if (this.paymentCheckoutService != null) {
            this.paymentCheckoutService.setBookingPricingPreviewService(bookingPricingPreviewService);
        }
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutPreviewResponse previewCheckout(
            UUID currentUserId,
            UUID bookingId,
            PaymentCheckoutPreviewRequest request
    ) {
        return paymentCheckoutService.previewCheckout(currentUserId, bookingId, request);
    }

    public PaymentCheckoutResponse checkout(UUID currentUserId, PaymentCheckoutRequest request) {
        return paymentCheckoutService.checkout(currentUserId, request);
    }

    public PaymentCheckoutResponse handleWebhook(PaymentWebhookRequest request) {
        return paymentWebhookService.handleWebhook(request);
    }

    public PaymentCheckoutResponse getByTarget(UUID currentUserId, PaymentTargetType targetType, UUID targetId) {
        return paymentOrderQueryService.getByTarget(currentUserId, targetType, targetId);
    }

    /**
     * Manual recovery is intentionally an explicit command. Read endpoints never contact PayOS
     * or write state; webhook and scheduled reconciliation remain the normal update sources.
     */
    public PaymentCheckoutResponse synchronizeProviderStatus(UUID currentUserId,
                                                              PaymentTargetType targetType,
                                                              UUID targetId) {
        paymentOrderQueryService.assertAccess(currentUserId, targetType, targetId);
        if (targetType != PaymentTargetType.BOOKING) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.BAD_REQUEST,
                    "Manual payment sync hiện chỉ hỗ trợ booking"
            );
        }
        paymentWebhookService.synchronizeProviderStatusForBooking(targetId);
        return paymentOrderQueryService.getByTarget(currentUserId, targetType, targetId);
    }

    public void synchronizeProviderStatusForBooking(UUID bookingId) {
        paymentWebhookService.synchronizeProviderStatusForBooking(bookingId);
    }

    public void reconcileStaleProviderPayments() {
        paymentWebhookService.reconcileStaleProviderPayments();
    }

    @Transactional
    public void handleMenteeCancellation(UUID bookingId, boolean lateCancellation) {
        paymentLifecycleService.handleMenteeCancellation(bookingId, lateCancellation);
    }

    @Transactional
    public void handleMentorCancellation(UUID bookingId) {
        paymentLifecycleService.handleMentorCancellation(bookingId);
    }

    @Transactional
    public void expireAwaitingPayment(UUID bookingId) {
        paymentLifecycleService.expireAwaitingPayment(bookingId);
    }

    // Helper method for unit tests calling private generateProviderOrderCode via reflection
    private long generateProviderOrderCode(UUID id, int attemptNo) {
        return paymentOrderCodeGenerator.generateProviderOrderCode(id, attemptNo);
    }
}
