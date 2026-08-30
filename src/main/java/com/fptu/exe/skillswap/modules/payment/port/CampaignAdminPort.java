package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignAnalyticsDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignBenefitCreateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignBenefitDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignBenefitUpdateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignCreateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignFilterQuery;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignStatusChangeCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignUpdateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminDashboardCampaignOverviewDto;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface CampaignAdminPort {
    PageResponse<AdminCampaignDto> list(AdminCampaignFilterQuery query);
    AdminCampaignDto getDetail(UUID campaignId);
    AdminCampaignDto create(UUID adminUserId, AdminCampaignCreateCommand command);
    AdminCampaignDto update(UUID adminUserId, UUID campaignId, AdminCampaignUpdateCommand command);
    AdminCampaignDto changeStatus(UUID adminUserId, UUID campaignId, AdminCampaignStatusChangeCommand command);
    AdminCampaignAnalyticsDto getAnalytics(UUID campaignId);
    List<AdminCampaignBenefitDto> listBenefits(UUID campaignId);
    AdminCampaignBenefitDto createBenefit(UUID adminUserId, UUID campaignId, AdminCampaignBenefitCreateCommand command);
    AdminCampaignBenefitDto updateBenefit(UUID adminUserId, UUID campaignId, UUID benefitId, AdminCampaignBenefitUpdateCommand command);
    void deleteBenefit(UUID adminUserId, UUID campaignId, UUID benefitId);
    AdminDashboardCampaignOverviewDto getDashboardCampaignOverview();
}
