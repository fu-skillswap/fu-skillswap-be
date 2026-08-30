package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record AdminCouponUpdateCommand(
        String code,
        String title,
        String description,
        CouponDiscountType discountType,
        Integer discountValue,
        Integer maxDiscountScoin,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer quotaTotal,
        Integer quotaPerUser,
        Integer minOrderValueScoin,
        Set<UUID> applicableServiceIds,
        Set<UUID> applicableMentorIds
) {}
