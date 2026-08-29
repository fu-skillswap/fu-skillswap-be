package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignBenefitCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignBenefitUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignAnalyticsResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignBenefitResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardCampaignOverviewResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface CampaignAdminPort {
    PageResponse<AdminCampaignResponse> list(AdminCampaignListRequest request);
    AdminCampaignResponse getDetail(UUID campaignId);
    AdminCampaignResponse create(UUID adminUserId, AdminCampaignCreateRequest request);
    AdminCampaignResponse update(UUID adminUserId, UUID campaignId, AdminCampaignUpdateRequest request);
    AdminCampaignResponse changeStatus(UUID adminUserId, UUID campaignId, AdminCampaignStatusRequest request);
    AdminCampaignAnalyticsResponse getAnalytics(UUID campaignId);
    List<AdminCampaignBenefitResponse> listBenefits(UUID campaignId);
    AdminCampaignBenefitResponse createBenefit(UUID adminUserId, UUID campaignId, AdminCampaignBenefitCreateRequest request);
    AdminCampaignBenefitResponse updateBenefit(UUID adminUserId, UUID campaignId, UUID benefitId, AdminCampaignBenefitUpdateRequest request);
    void deleteBenefit(UUID adminUserId, UUID campaignId, UUID benefitId);
    AdminDashboardCampaignOverviewResponse getDashboardCampaignOverview();
}
