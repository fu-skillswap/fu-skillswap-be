package com.fptu.exe.skillswap.modules.payment.port;

/** Campaign and coupon aggregate metrics exposed to the admin dashboard. */
public record AdminDashboardCampaignOverview(
        long activeCampaignCount,
        long scheduledCampaignCount,
        long totalBudgetScoin,
        long totalBudgetUsedScoin,
        long activeCouponCount,
        long totalCouponRedemptions
) { }
