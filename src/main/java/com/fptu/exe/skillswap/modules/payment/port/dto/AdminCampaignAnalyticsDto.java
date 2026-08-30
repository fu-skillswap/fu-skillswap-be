package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminCampaignAnalyticsDto(
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
) {}
