package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Provider callback response boundary. The fields intentionally mirror the
 * existing callback JSON so provider integrations remain compatible.
 */
@Schema(description = "Internal/System - phản hồi cho callback payment provider, không dùng cho FE. Các mã provider chỉ phục vụ đối chiếu và vận hành.")
public record InternalPaymentWebhookResponse(
        UUID paymentOrderId,
        String orderCode,
        UUID bookingId,
        Integer attemptNo,
        Integer priceScoin,
        Integer couponDiscountScoin,
        Integer campaignCreditAppliedScoin,
        Integer userCreditAppliedScoin,
        Integer remainingPayableScoin,
        Integer remainingPayableVnd,
        PaymentOrderStatus status,
        PaymentProvider paymentProvider,
        String providerOrderCode,
        String providerPaymentLinkId,
        String providerStatus,
        String checkoutUrl,
        String paymentLink,
        OffsetDateTime expiresAt,
        String userActionMessage,
        boolean retryable
) {
    public static InternalPaymentWebhookResponse from(PaymentCheckoutResponse response) {
        if (response == null) {
            return null;
        }
        return new InternalPaymentWebhookResponse(
                response.paymentOrderId(), response.orderCode(), response.bookingId(), response.attemptNo(),
                response.priceScoin(), response.couponDiscountScoin(), response.campaignCreditAppliedScoin(),
                response.userCreditAppliedScoin(), response.remainingPayableScoin(), response.remainingPayableVnd(),
                response.status(), response.paymentProvider(), response.providerOrderCode(),
                response.providerPaymentLinkId(), response.providerStatus(), response.checkoutUrl(),
                response.paymentLink(), response.expiresAt(), response.userActionMessage(), response.retryable());
    }
}
