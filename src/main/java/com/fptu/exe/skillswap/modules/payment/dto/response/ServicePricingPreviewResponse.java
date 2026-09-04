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
        @Schema(description = "Giá dịch vụ cuối cùng theo Scoin trước khi áp credit.")
        Integer priceScoin,
        @Schema(description = "Giá trước khi áp campaign.")
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
}
