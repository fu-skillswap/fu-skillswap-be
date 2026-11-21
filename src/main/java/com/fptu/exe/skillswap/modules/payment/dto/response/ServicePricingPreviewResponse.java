package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Bản ước tính giá dịch vụ cho tài khoản hiện tại. Đây không phải cam kết cuối cùng; giá được tính lại khi tạo booking/payment.")
public record ServicePricingPreviewResponse(
        @Schema(description = "Internal field - FE không cần sử dụng. Phiên bản bảng giá dùng để kiểm tra/debug.")
        String pricingVersion,
        @Schema(description = "Thời điểm tính giá kèm offset +07:00", example = "2026-08-24T19:00:00+07:00")
        OffsetDateTime calculatedAt,
        @Schema(description = "ID dịch vụ được xem giá.") UUID serviceId,
        @Schema(description = "Giá dịch vụ gốc theo Scoin trước khi áp coupon/campaign.")
        Integer priceScoin,
        @Schema(description = "Giá gốc ban đầu trước khi trừ coupon để hiển thị gạch ngang.")
        Integer originalPriceScoin,
        @Schema(description = "Giá mới sau khi trừ coupon ưu đãi (bằng originalPriceScoin nếu không có coupon).")
        Integer discountedPriceScoin,
        @Schema(description = "Số Scoin được giảm bởi coupon khả dụng.")
        Integer couponDiscountScoin,
        @Schema(description = "Có đang áp dụng coupon giảm giá hay không.")
        boolean hasCouponDiscount,
        @Schema(description = "Mã coupon khả dụng được ước tính áp dụng, null nếu không có.", nullable = true)
        String couponCode,
        @Schema(description = "Tiêu đề coupon khả dụng, null nếu không có.", nullable = true)
        String couponTitle,
        @Schema(description = "Số suất coupon còn lại của hệ thống, null nếu không giới hạn hoặc không có coupon.", nullable = true)
        Integer couponRemainingQuota,
        @Schema(description = "Giá trước khi áp campaign (sau khi trừ coupon nếu có).")
        Integer priceBeforeCampaignScoin,
        @Schema(description = "Số Scoin được giảm bởi campaign.")
        Integer campaignDiscountScoin,
        @Schema(description = "Số Scoin ước tính người dùng phải trả.")
        Integer estimatedPayableScoin,
        @Schema(description = "Tên campaign được áp dụng nếu có.", nullable = true)
        String campaignName,
        @Schema(description = "Cho biết đây là giá ước tính, không phải kết quả thanh toán cuối cùng.")
        boolean isEstimate,
        @Schema(description = "Lưu ý hiển thị cho người dùng.", nullable = true)
        String disclaimer
) {
    /** Compatibility constructor for callers without coupon preview fields. */
    public ServicePricingPreviewResponse(
            String pricingVersion,
            OffsetDateTime calculatedAt,
            UUID serviceId,
            Integer priceScoin,
            Integer priceBeforeCampaignScoin,
            Integer campaignDiscountScoin,
            Integer estimatedPayableScoin,
            String campaignName,
            boolean isEstimate,
            String disclaimer
    ) {
        this(
                pricingVersion,
                calculatedAt,
                serviceId,
                priceScoin,
                priceScoin,
                priceScoin,
                0,
                false,
                null,
                null,
                null,
                priceBeforeCampaignScoin,
                campaignDiscountScoin,
                estimatedPayableScoin,
                campaignName,
                isEstimate,
                disclaimer
        );
    }
}
