package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefitType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;

public record AdminCampaignBenefitUpdateCommand(
        CampaignBenefitType benefitType,
        Integer creditScoin,
        String couponCode,
        CouponDiscountType couponDiscountType,
        Integer couponDiscountValue,
        Integer couponMaxDiscountScoin,
        Integer couponQuotaTotal,
        Integer couponQuotaPerUser,
        Integer couponMinOrderValueScoin,
        Boolean active
) {}
