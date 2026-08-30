package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;

public record AdminCampaignStatusChangeCommand(
        CampaignStatus status
) {}
