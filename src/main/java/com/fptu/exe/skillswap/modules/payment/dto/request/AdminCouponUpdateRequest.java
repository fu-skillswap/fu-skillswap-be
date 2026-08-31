package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Request cập nhật coupon cho admin (partial update).")
public record AdminCouponUpdateRequest(
        @Schema(description = "Mã coupon")
        @Size(max = 80, message = "Mã coupon không quá 80 ký tự")
        String code,

        @Schema(description = "Tiêu đề coupon")
        @Size(max = 150, message = "Tiêu đề coupon không quá 150 ký tự")
        String title,

        @Schema(description = "Mô tả chi tiết coupon")
        String description,

        @Schema(description = "Loại giảm giá")
        CouponDiscountType discountType,

        @Schema(description = "Giá trị giảm giá")
        @Min(value = 1, message = "Giá trị giảm giá phải >= 1")
        Integer discountValue,

        @Schema(description = "Số Scoin giảm tối đa khi loại giảm giá là PERCENT")
        @Min(value = 0, message = "Giảm tối đa phải >= 0")
        Integer maxDiscountScoin,

        @Schema(description = "Thời điểm bắt đầu có hiệu lực")
        LocalDateTime startAt,

        @Schema(description = "Thời điểm hết hiệu lực")
        LocalDateTime endAt,

        @Schema(description = "Tổng quota số lần sử dụng coupon toàn hệ thống")
        @Min(value = 1, message = "Quota tổng phải >= 1")
        Integer quotaTotal,

        @Schema(description = "Số lần sử dụng tối đa của mỗi người dùng")
        @Min(value = 1, message = "Quota mỗi user phải >= 1")
        Integer quotaPerUser,

        @Schema(description = "Giá trị đơn hàng tối thiểu (Scoin)")
        @Min(value = 0, message = "Giá trị đơn tối thiểu phải >= 0")
        Integer minOrderValueScoin,

        @Schema(description = "Tập hợp service ID được áp dụng coupon")
        Set<UUID> applicableServiceIds,

        @Schema(description = "Tập hợp mentor user ID được áp dụng coupon")
        Set<UUID> applicableMentorIds
) {
}
