package com.fptu.exe.skillswap.modules.admin.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Thông tin chi tiết coupon cho admin.")
public record AdminCouponResponse(
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
        Set<UUID> applicableHelpTopicIds,
        long totalRedemptions,
        long activeRedemptions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
