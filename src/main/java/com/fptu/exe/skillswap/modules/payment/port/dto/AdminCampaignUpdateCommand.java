package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record AdminCampaignUpdateCommand(
        String name,
        String description,
        FundingSource fundingSource,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer budgetScoin,
        Set<String> audienceRoleCodes,
        Set<UUID> audienceCampusIds,
        Set<UUID> audienceProgramIds,
        Set<UUID> audienceSpecializationIds
) {}
