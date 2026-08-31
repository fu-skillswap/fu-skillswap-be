package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefitType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Thông tin chi tiết campaign benefit cho admin.")
public record AdminCampaignBenefitResponse(
        UUID id,
        UUID campaignId,
        CampaignBenefitType benefitType,
        Integer creditScoin,
        String couponCode,
        CouponDiscountType couponDiscountType,
        Integer couponDiscountValue,
        Integer couponMaxDiscountScoin,
        Integer couponQuotaTotal,
        Integer couponQuotaPerUser,
        Integer couponMinOrderValueScoin,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
