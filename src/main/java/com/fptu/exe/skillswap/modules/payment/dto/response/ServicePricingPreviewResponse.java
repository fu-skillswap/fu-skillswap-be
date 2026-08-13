package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Non-binding service price estimate for the authenticated viewer")
public record ServicePricingPreviewResponse(
        String pricingVersion,
        LocalDateTime calculatedAt,
        UUID serviceId,
        @Schema(description = "The 110% final service price to be paid by Mentee")
        Integer priceScoin,
        Integer priceBeforeCampaignScoin,
        Integer campaignDiscountScoin,
        Integer estimatedPayableScoin,
        String campaignName,
        boolean isEstimate,
        String disclaimer
) {
}
