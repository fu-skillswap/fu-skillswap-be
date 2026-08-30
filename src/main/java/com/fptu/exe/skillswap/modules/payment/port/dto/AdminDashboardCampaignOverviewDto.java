package com.fptu.exe.skillswap.modules.payment.port.dto;

public record AdminDashboardCampaignOverviewDto(
        long activeCampaignCount,
        long scheduledCampaignCount,
        long totalBudgetScoin,
        long totalBudgetUsedScoin,
        long activeCouponCount,
        long totalCouponRedemptions
) {}
