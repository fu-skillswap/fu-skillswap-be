package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Kết quả checkout payment cho booking")
    public record PaymentCheckoutResponse(
        UUID paymentOrderId,
        String orderCode,
        UUID bookingId,
        Integer attemptNo,
        @Schema(description = "The 110% final service price to be paid by Mentee")
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
        @Schema(description = "Thời điểm hết hạn thanh toán kèm offset +07:00", example = "2026-08-24T20:00:00+07:00")
        OffsetDateTime expiresAt
) {
}
