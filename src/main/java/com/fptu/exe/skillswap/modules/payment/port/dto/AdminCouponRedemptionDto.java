package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminCouponRedemptionDto(
        UUID id,
        UUID couponId,
        UUID paymentOrderId,
        UUID redeemerUserId,
        String redeemerFullName,
        CouponRedemptionStatus status,
        Integer discountScoin,
        LocalDateTime createdAt
) {}
