package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Báo cáo phân tích hiệu quả chiến dịch (Campaign Analytics).")
public record AdminCampaignAnalyticsResponse(
        UUID campaignId,
        String campaignName,
        CampaignStatus status,
        int budgetScoin,
        int budgetUsedScoin,
        int budgetRemainingScoin,
        double budgetBurnRate,
        long totalBookingsCreated,
        long totalCouponRedemptions,
        int totalRevenueScoin,
        int totalCampaignCostScoin,
        double campaignRoiPercent,
        LocalDateTime startAt,
        LocalDateTime endAt,
        long daysActive
) {
}
