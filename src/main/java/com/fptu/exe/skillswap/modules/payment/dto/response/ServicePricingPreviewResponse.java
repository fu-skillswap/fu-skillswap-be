package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Non-binding service price estimate for the authenticated viewer")
public record ServicePricingPreviewResponse(
        String pricingVersion,
        @Schema(description = "Thời điểm tính giá kèm offset +07:00", example = "2026-08-24T19:00:00+07:00")
        OffsetDateTime calculatedAt,
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
