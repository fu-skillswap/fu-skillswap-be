package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tổng quan số liệu campaign và coupon cho admin dashboard.")
public record AdminDashboardCampaignOverviewResponse(
        @Schema(description = "Số campaign đang ACTIVE", example = "3")
        long activeCampaignCount,
        @Schema(description = "Số campaign đang SCHEDULED", example = "2")
        long scheduledCampaignCount,
        @Schema(description = "Tổng ngân sách Scoin của tất cả campaign ACTIVE", example = "500000")
        long totalBudgetScoin,
        @Schema(description = "Tổng ngân sách Scoin đã sử dụng của các campaign ACTIVE", example = "120000")
        long totalBudgetUsedScoin,
        @Schema(description = "Số coupon đang ACTIVE", example = "5")
        long activeCouponCount,
        @Schema(description = "Tổng lượt redemption coupon toàn hệ thống", example = "450")
        long totalCouponRedemptions
) {
}
