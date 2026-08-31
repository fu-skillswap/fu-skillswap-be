package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Thông tin sử dụng coupon (Coupon Redemption) cho admin.")
public record AdminCouponRedemptionResponse(
        UUID id,
        UUID couponId,
        UUID paymentOrderId,
        UUID redeemerUserId,
        String redeemerFullName,
        CouponRedemptionStatus status,
        Integer discountScoin,
        LocalDateTime createdAt
) {
}
