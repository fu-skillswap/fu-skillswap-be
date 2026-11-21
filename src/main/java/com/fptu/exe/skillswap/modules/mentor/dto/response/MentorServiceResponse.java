package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;

@Builder
@Schema(description = "Mentoring service definition that the mentor manages and the frontend can show in mentor detail or booking flows.")
public record MentorServiceResponse(
        @Schema(description = "Service ID", example = "019f3234-aaaa-bbbb-cccc-1234567890ab")
        UUID serviceId,
        @Schema(description = "Mentor user ID that owns the service", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID mentorUserId,
        @Schema(description = "Service title shown in mentor detail and booking selection", example = "Review CV xin thực tập")
        String title,
        @Schema(description = "Service description shown to mentees before booking", example = "Mình sẽ review CV, chỉ ra điểm yếu và gợi ý cách cải thiện theo vị trí backend intern.")
        String description,
        @Schema(description = "Expected outcome after the mentoring service finishes", example = "CV rõ điểm mạnh, có action items cụ thể sau buổi mentoring.")
        String expectedOutcome,
        @Schema(description = "Service duration in minutes", example = "60")
        Integer durationMinutes,
        @Schema(description = "Whether the service is free", example = "true")
        boolean isFree,
        @Schema(description = "The 110% final service price to be paid by Mentee before coupon discount. 0 means the service is free.", nullable = true, example = "110000")
        Integer priceScoin,
        @Schema(description = "Giá gốc ban đầu trước khi trừ coupon để hiển thị gạch ngang (= priceScoin).", nullable = true, example = "110000")
        Integer originalPriceScoin,
        @Schema(description = "Giá mới sau khi trừ coupon ưu đãi khả dụng (bằng originalPriceScoin nếu không có coupon áp dụng).", nullable = true, example = "40000")
        Integer discountedPriceScoin,
        @Schema(description = "Số Scoin được giảm bởi coupon khả dụng (0 nếu không có coupon hoặc đã hết suất).", nullable = true, example = "70000")
        Integer couponDiscountScoin,
        @Schema(description = "Có đang được giảm giá bởi coupon hay không.", example = "true")
        boolean hasCouponDiscount,
        @Schema(description = "Mã coupon khả dụng đang được áp dụng để tính giá preview, null nếu không có.", nullable = true, example = "WELCOME70K")
        String couponCode,
        @Schema(description = "Tiêu đề coupon khả dụng, null nếu không có.", nullable = true)
        String couponTitle,
        @Schema(description = "Số suất coupon còn lại của hệ thống, null nếu không giới hạn hoặc không có coupon.", nullable = true, example = "60")
        Integer couponRemainingQuota,
        @Schema(description = "Whether the service is currently active for future use", example = "true")
        boolean isActive,
        @Schema(description = "Whether a completed booking keeps direct chat open after the post-session window", example = "false")
        boolean maintainPostSessionChat,
        @Schema(description = "Immutable delivery format.", example = "ONE_TO_ONE")
        MentorServiceDeliveryMode deliveryMode,
        @Schema(description = "URL public của ảnh bìa dịch vụ nếu có", example = "https://cdn.skillswap.asia/services/cover.jpg", nullable = true)
        String coverImageUrl,
        @Schema(description = "Optimistic-lock version for management mutations", example = "4")
        Integer version,
        @Schema(description = "Service creation time", example = "2026-06-20T09:00:00")
        LocalDateTime createdAt,
        @Schema(description = "Latest service update time", example = "2026-06-24T10:00:00")
        LocalDateTime updatedAt
) {
    @Deprecated(forRemoval = true)
    public boolean free() {
        return isFree;
    }

    @Deprecated(forRemoval = true)
    public boolean active() {
        return isActive;
    }

    public MentorServiceResponse withPricingPreview(
            Integer originalPriceScoin,
            Integer discountedPriceScoin,
            Integer couponDiscountScoin,
            boolean hasCouponDiscount,
            String couponCode,
            String couponTitle,
            Integer couponRemainingQuota
    ) {
        return new MentorServiceResponse(
                serviceId,
                mentorUserId,
                title,
                description,
                expectedOutcome,
                durationMinutes,
                isFree,
                originalPriceScoin,
                originalPriceScoin,
                discountedPriceScoin,
                couponDiscountScoin,
                hasCouponDiscount,
                couponCode,
                couponTitle,
                couponRemainingQuota,
                isActive,
                maintainPostSessionChat,
                deliveryMode,
                coverImageUrl,
                version,
                createdAt,
                updatedAt
        );
    }

    /** Keeps Java test fixtures compiling while JSON exposes only isFree/isActive. */
    public static class MentorServiceResponseBuilder {
        public MentorServiceResponseBuilder free(boolean value) {
            return isFree(value);
        }

        public MentorServiceResponseBuilder active(boolean value) {
            return isActive(value);
        }
    }
}
