package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Read-only checkout estimate. Final amounts are recalculated by checkout.")
public record PaymentCheckoutPreviewResponse(
        UUID bookingId,
        Integer basePriceScoin,
        Integer menteeSurchargeScoin,
        Integer priceBeforeDiscountScoin,
        Integer couponDiscountScoin,
        Integer campaignCreditAppliedScoin,
        Integer userCreditAppliedScoin,
        Integer estimatedFinalPayableScoin,
        LocalDateTime paymentDeadlineAt,
        boolean isEstimate,
        String disclaimer
) {
}
