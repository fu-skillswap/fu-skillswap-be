package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Thông tin coupon khả dụng cho người dùng")
public record UserCouponResponse(
        @Schema(description = "ID của coupon")
        UUID id,

        @Schema(description = "Mã coupon", example = "WELCOME70K")
        String code,

        @Schema(description = "Tiêu đề coupon", example = "Mã chào mừng thành viên mới")
        String title,

        @Schema(description = "Mô tả chi tiết coupon", example = "Giảm ngay 70.000 VNĐ cho buổi học đầu tiên")
        String description,

        @Schema(description = "Loại giảm giá", example = "FIXED")
        CouponDiscountType discountType,

        @Schema(description = "Giá trị giảm", example = "70000")
        Integer discountValue,

        @Schema(description = "Mức giảm tối đa (nếu PERCENT)", example = "70000")
        Integer maxDiscountScoin,

        @Schema(description = "Giá trị đơn tối thiểu để áp dụng", example = "0")
        Integer minOrderValueScoin,

        @Schema(description = "Tổng quota hệ thống", example = "60")
        Integer quotaTotal,

        @Schema(description = "Số suất còn lại trong hệ thống", example = "42")
        Integer remainingQuota,

        @Schema(description = "Coupon có thể sử dụng bởi tài khoản này hay không", example = "true")
        boolean isUsable,

        @Schema(description = "Lý do không thể sử dụng nếu isUsable = false", example = "Bạn đã sử dụng hết lượt coupon này")
        String unusableReason
) {}
