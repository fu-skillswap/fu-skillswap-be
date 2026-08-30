package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignBenefitCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignBenefitUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignAnalyticsResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignBenefitResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignResponse;
import com.fptu.exe.skillswap.modules.payment.port.CampaignAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCampaignService {

    private final CampaignAdminPort campaignAdminPort;

    @Transactional(readOnly = true)
    public PageResponse<AdminCampaignResponse> list(AdminCampaignListRequest request) {
        return campaignAdminPort.list(request);
    }

    @Transactional(readOnly = true)
    public AdminCampaignResponse getDetail(UUID campaignId) {
        return campaignAdminPort.getDetail(campaignId);
    }

    @Transactional
    public AdminCampaignResponse create(UUID adminUserId, AdminCampaignCreateRequest request) {
        return campaignAdminPort.create(adminUserId, request);
    }

    @Transactional
    public AdminCampaignResponse update(UUID adminUserId, UUID campaignId, AdminCampaignUpdateRequest request) {
        return campaignAdminPort.update(adminUserId, campaignId, request);
    }

    @Transactional
    public AdminCampaignResponse changeStatus(UUID adminUserId, UUID campaignId, AdminCampaignStatusRequest request) {
        return campaignAdminPort.changeStatus(adminUserId, campaignId, request);
    }

    @Transactional(readOnly = true)
    public AdminCampaignAnalyticsResponse getAnalytics(UUID campaignId) {
        return campaignAdminPort.getAnalytics(campaignId);
    }

    @Transactional(readOnly = true)
    public List<AdminCampaignBenefitResponse> listBenefits(UUID campaignId) {
        return campaignAdminPort.listBenefits(campaignId);
    }

    @Transactional
    public AdminCampaignBenefitResponse createBenefit(UUID adminUserId, UUID campaignId, AdminCampaignBenefitCreateRequest request) {
        return campaignAdminPort.createBenefit(adminUserId, campaignId, request);
    }

    @Transactional
    public AdminCampaignBenefitResponse updateBenefit(UUID adminUserId, UUID campaignId, UUID benefitId, AdminCampaignBenefitUpdateRequest request) {
        return campaignAdminPort.updateBenefit(adminUserId, campaignId, benefitId, request);
    }

    @Transactional
    public void deleteBenefit(UUID adminUserId, UUID campaignId, UUID benefitId) {
        campaignAdminPort.deleteBenefit(adminUserId, campaignId, benefitId);
    }
}
