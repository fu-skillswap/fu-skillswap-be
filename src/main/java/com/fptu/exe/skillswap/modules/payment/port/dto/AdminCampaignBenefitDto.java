package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefitType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminCampaignBenefitDto(
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
) {}
