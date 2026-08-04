package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefitType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request tạo benefit cho campaign.")
public record AdminCampaignBenefitCreateRequest(
        @Schema(description = "Loại quyền lợi campaign (CREDIT_ISSUANCE hoặc COUPON_ISSUANCE)", example = "CREDIT_ISSUANCE")
        @NotNull(message = "Loại quyền lợi không được để trống")
        CampaignBenefitType benefitType,

        @Schema(description = "Số lượng Scoin trợ giá trực tiếp khi type = CREDIT_ISSUANCE", example = "50000")
        @Min(value = 0, message = "creditScoin phải >= 0")
        Integer creditScoin,

        @Schema(description = "Mã coupon liên kết khi type = COUPON_ISSUANCE")
        String couponCode,

        @Schema(description = "Loại giảm giá của coupon nếu tự sinh coupon")
        CouponDiscountType couponDiscountType,

        @Schema(description = "Giá trị giảm giá của coupon")
        @Min(value = 0, message = "couponDiscountValue phải >= 0")
        Integer couponDiscountValue,

        @Schema(description = "Giảm tối đa theo Scoin nếu couponDiscountType = PERCENT")
        @Min(value = 0, message = "couponMaxDiscountScoin phải >= 0")
        Integer couponMaxDiscountScoin,

        @Schema(description = "Tổng quota lượt dùng coupon của benefit này")
        @Min(value = 0, message = "couponQuotaTotal phải >= 0")
        Integer couponQuotaTotal,

        @Schema(description = "Số lượt dùng coupon tối đa mỗi user")
        @Min(value = 0, message = "couponQuotaPerUser phải >= 0")
        Integer couponQuotaPerUser,

        @Schema(description = "Giá trị đơn tối thiểu để dùng coupon (Scoin)")
        @Min(value = 0, message = "couponMinOrderValueScoin phải >= 0")
        Integer couponMinOrderValueScoin
) {
}
