package com.fptu.exe.skillswap.modules.admin.service;

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
    public PageResponse<CampaignAdminPort.CampaignView> list(CampaignAdminPort.CampaignListQuery request) {
        return campaignAdminPort.list(request);
    }

    @Transactional(readOnly = true)
    public CampaignAdminPort.CampaignView getDetail(UUID campaignId) {
        return campaignAdminPort.getDetail(campaignId);
    }

    @Transactional
    public CampaignAdminPort.CampaignView create(UUID adminUserId, CampaignAdminPort.CreateCampaignCommand request) {
        return campaignAdminPort.create(adminUserId, request);
    }

    @Transactional
    public CampaignAdminPort.CampaignView update(UUID adminUserId, UUID campaignId, CampaignAdminPort.UpdateCampaignCommand request) {
        return campaignAdminPort.update(adminUserId, campaignId, request);
    }

    @Transactional
    public CampaignAdminPort.CampaignView changeStatus(UUID adminUserId, UUID campaignId, CampaignAdminPort.ChangeCampaignStatusCommand request) {
        return campaignAdminPort.changeStatus(adminUserId, campaignId, request);
    }

    @Transactional(readOnly = true)
    public CampaignAdminPort.CampaignAnalyticsView getAnalytics(UUID campaignId) {
        return campaignAdminPort.getAnalytics(campaignId);
    }

    @Transactional(readOnly = true)
    public List<CampaignAdminPort.CampaignBenefitView> listBenefits(UUID campaignId) {
        return campaignAdminPort.listBenefits(campaignId);
    }

    @Transactional
    public CampaignAdminPort.CampaignBenefitView createBenefit(UUID adminUserId, UUID campaignId, CampaignAdminPort.CreateCampaignBenefitCommand request) {
        return campaignAdminPort.createBenefit(adminUserId, campaignId, request);
    }

    @Transactional
    public CampaignAdminPort.CampaignBenefitView updateBenefit(UUID adminUserId, UUID campaignId, UUID benefitId, CampaignAdminPort.UpdateCampaignBenefitCommand request) {
        return campaignAdminPort.updateBenefit(adminUserId, campaignId, benefitId, request);
    }

    @Transactional
    public void deleteBenefit(UUID adminUserId, UUID campaignId, UUID benefitId) {
        campaignAdminPort.deleteBenefit(adminUserId, campaignId, benefitId);
    }
}
