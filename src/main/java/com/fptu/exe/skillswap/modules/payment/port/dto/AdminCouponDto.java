package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record AdminCouponDto(
        UUID id,
        String code,
        String title,
        String description,
        CouponDiscountType discountType,
        Integer discountValue,
        Integer maxDiscountScoin,
        CouponStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer quotaTotal,
        Integer quotaPerUser,
        Integer minOrderValueScoin,
        Set<UUID> applicableServiceIds,
        Set<UUID> applicableMentorIds,
        long totalRedemptions,
        long activeRedemptions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
