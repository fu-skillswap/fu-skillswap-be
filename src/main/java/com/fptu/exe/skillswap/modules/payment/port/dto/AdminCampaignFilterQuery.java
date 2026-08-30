package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCampaignFilterQuery extends BasePageRequest {
    private CampaignStatus status;
    private FundingSource fundingSource;
    private String keyword;
}
