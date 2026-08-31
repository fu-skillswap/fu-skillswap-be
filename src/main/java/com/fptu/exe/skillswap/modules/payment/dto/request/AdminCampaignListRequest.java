package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCampaignListRequest extends BasePageRequest {

    @Schema(description = "Lọc theo trạng thái campaign", example = "ACTIVE")
    private CampaignStatus status;

    @Schema(description = "Lọc theo nguồn tài trợ", example = "APP_FUNDED")
    private FundingSource fundingSource;

    @Schema(description = "Tìm kiếm theo tên campaign")
    private String keyword;

    public AdminCampaignListRequest() {
        setSortBy("createdAt");
        setDirection("DESC");
        setSize(20);
    }
}
