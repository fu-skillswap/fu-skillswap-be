package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record AdminCampaignDto(
        UUID id,
        String name,
        String description,
        CampaignStatus status,
        FundingSource fundingSource,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer budgetScoin,
        Integer budgetUsedScoin,
        Integer budgetRemainingScoin,
        Set<String> audienceRoleCodes,
        Set<UUID> audienceCampusIds,
        Set<UUID> audienceProgramIds,
        Set<UUID> audienceSpecializationIds,
        long benefitCount,
        long totalBookingsCreated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
