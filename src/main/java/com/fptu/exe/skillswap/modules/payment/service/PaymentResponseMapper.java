package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.shared.time.BusinessTime;
import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PaymentResponseMapper {

    private final PaymentProperties paymentProperties;

    public PaymentCheckoutResponse toResponse(PaymentOrder order, PaymentAttempt attempt) {
        if (order == null) {
            return null;
        }
        return PaymentCheckoutResponse.builder()
                .paymentOrderId(order.getId())
                .orderCode(order.getOrderCode())
                .bookingId(order.getTargetId())
                .attemptNo(attempt == null ? null : attempt.getAttemptNo())
                .priceScoin(order.getGrossScoin())
                .couponDiscountScoin(order.getCouponDiscountScoin())
                .campaignCreditAppliedScoin(order.getCampaignCreditScoin())
                .userCreditAppliedScoin(order.getUserCreditScoin())
                .remainingPayableScoin(order.getRemainingPayableScoin())
                .remainingPayableVnd(safeVnd(order.getRemainingPayableScoin()))
                .status(order.getStatus())
                .paymentProvider(order.getPaymentProvider())
                .providerOrderCode(attempt != null && StringUtils.hasText(attempt.getProviderOrderCode())
                        ? attempt.getProviderOrderCode()
                        : order.getProviderOrderCode())
                .providerPaymentLinkId(attempt != null && StringUtils.hasText(attempt.getProviderPaymentLinkId())
                        ? attempt.getProviderPaymentLinkId()
                        : order.getProviderPaymentLinkId())
                .providerStatus(attempt != null && StringUtils.hasText(attempt.getProviderStatus())
                        ? attempt.getProviderStatus()
                        : order.getProviderStatus())
                .checkoutUrl(attempt == null ? order.getPaymentLink() : attempt.getCheckoutUrl())
                .paymentLink(order.getPaymentLink())
                .expiresAt(order.getExpiresAtUtc() != null
                        ? BusinessTime.toOffsetDateTime(order.getExpiresAtUtc())
                        : (order.getExpiresAt() != null ? BusinessTime.toOffsetDateTime(order.getExpiresAt()) : null))
                .userActionMessage(userActionMessage(order.getStatus()))
                .retryable(retryable(order.getStatus()))
                .build();
    }

    private String userActionMessage(com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus status) {
        if (status == null) return null;
        return switch (status) {
            case PENDING, AWAITING_PROVIDER_PAYMENT -> "Vui lòng hoàn tất thanh toán trước thời hạn.";
            case PARTIALLY_COVERED_BY_CREDIT -> "Một phần số dư đã được áp dụng; vui lòng hoàn tất phần còn lại.";
            case PAID -> "Thanh toán đã được ghi nhận.";
            case FAILED -> "Thanh toán chưa thành công. Bạn có thể bắt đầu lại theo hướng dẫn.";
            case CANCELLED -> "Phiên thanh toán đã bị hủy. Vui lòng tạo phiên mới nếu cần.";
            case EXPIRED -> "Phiên thanh toán đã hết hạn. Vui lòng tạo phiên mới.";
        };
    }

    private boolean retryable(com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus status) {
        return status == com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus.FAILED
                || status == com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus.EXPIRED;
    }

    private Integer safeVnd(Integer scoin) {
        long value = PricingPolicy.toVnd(scoin == null ? 0 : scoin, paymentProperties);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Số tiền VND vượt giới hạn response");
        }
        return (int) value;
    }
}
