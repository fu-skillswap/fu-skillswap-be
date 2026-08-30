package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.shared.policy.PricingPolicy;

import com.fptu.exe.skillswap.shared.time.BookingTime;
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
                        ? BookingTime.toOffsetDateTime(order.getExpiresAtUtc())
                        : (order.getExpiresAt() != null ? BookingTime.toOffsetDateTime(order.getExpiresAt()) : null))
                .build();
    }

    private Integer safeVnd(Integer scoin) {
        long value = PricingPolicy.toVnd(scoin == null ? 0 : scoin, paymentProperties);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Số tiền VND vượt giới hạn response");
        }
        return (int) value;
    }
}
