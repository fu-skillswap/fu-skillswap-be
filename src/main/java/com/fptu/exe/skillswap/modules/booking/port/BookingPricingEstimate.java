package com.fptu.exe.skillswap.modules.booking.port;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Non-binding service price estimate for the booking quote")
public record BookingPricingEstimate(
        String pricingVersion,
        @Schema(description = "Thời điểm tính giá kèm offset +07:00", example = "2026-08-24T19:00:00+07:00")
        OffsetDateTime calculatedAt,
        UUID serviceId,
        @Schema(description = "The 110% final service price to be paid by Mentee before coupon/campaign discount")
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
        Integer priceBeforeCampaignScoin,
        Integer campaignDiscountScoin,
        Integer estimatedPayableScoin,
        String campaignName,
        boolean isEstimate,
        String disclaimer
) {
    /** Compatibility constructor for existing callers/tests without coupon breakdown fields. */
    public BookingPricingEstimate(
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
