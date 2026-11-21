package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Thông tin service cơ bản được gắn vào availability slot")
public record AvailabilitySlotServiceBasicResponse(
        @Schema(description = "ID của service mà mentee sẽ truyền tiếp vào API candidates hoặc create booking")
        UUID serviceId,
        @Schema(description = "Tên service")
        String title,
        @Schema(description = "Thời lượng thực của service tính theo phút. Exact candidate segments sẽ được cắt theo duration này.")
        Integer durationMinutes,
        @Schema(description = "Service miễn phí hay trả phí")
        boolean isFree,
        @Schema(description = "Đơn giá service gốc theo Scoin (110% đã gồm phí nền tảng) nếu không miễn phí")
        Integer priceScoin,
        @Schema(description = "Giá gốc ban đầu trước khi trừ coupon để hiển thị gạch ngang")
        Integer originalPriceScoin,
        @Schema(description = "Giá mới sau khi trừ coupon ưu đãi khả dụng")
        Integer discountedPriceScoin,
        @Schema(description = "Số Scoin được giảm bởi coupon khả dụng")
        Integer couponDiscountScoin,
        @Schema(description = "Có đang áp dụng coupon giảm giá hay không")
        boolean hasCouponDiscount,
        @Schema(description = "Mã coupon khả dụng được áp dụng preview, null nếu không có", nullable = true)
        String couponCode,
        @Schema(description = "Số suất coupon còn lại của hệ thống, null nếu không giới hạn hoặc không có coupon", nullable = true)
        Integer couponRemainingQuota,
        @Schema(description = "Khả năng gỡ binding service này khỏi slot")
        SlotMutationCapabilityResponse bindingRemoval
) {
    /** Compatibility constructor for mentor-owned slot management callers without promo preview fields. */
    public AvailabilitySlotServiceBasicResponse(
            UUID serviceId,
            String title,
            Integer durationMinutes,
            boolean isFree,
            Integer priceScoin,
            SlotMutationCapabilityResponse bindingRemoval
    ) {
        this(
                serviceId,
                title,
                durationMinutes,
                isFree,
                priceScoin,
                priceScoin,
                priceScoin,
                0,
                false,
                null,
                null,
                bindingRemoval
        );
    }
}
