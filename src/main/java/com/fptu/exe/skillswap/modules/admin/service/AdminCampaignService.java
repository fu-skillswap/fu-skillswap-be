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
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignAnalyticsDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignBenefitCreateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignBenefitDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignBenefitUpdateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignCreateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignFilterQuery;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignStatusChangeCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCampaignUpdateCommand;
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
        AdminCampaignFilterQuery query = new AdminCampaignFilterQuery();
        if (request != null) {
            query.setStatus(request.getStatus());
            query.setFundingSource(request.getFundingSource());
            query.setKeyword(request.getKeyword());
            query.setPage(request.getPage());
            query.setSize(request.getSize());
            query.setSortBy(request.getSortBy());
            query.setDirection(request.getDirection());
        }
        PageResponse<AdminCampaignDto> result = campaignAdminPort.list(query);
        return PageResponse.<AdminCampaignResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminCampaignResponse getDetail(UUID campaignId) {
        return toResponse(campaignAdminPort.getDetail(campaignId));
    }

    @Transactional
    public AdminCampaignResponse create(UUID adminUserId, AdminCampaignCreateRequest request) {
        AdminCampaignCreateCommand command = new AdminCampaignCreateCommand(
                request.name(),
                request.description(),
                request.fundingSource(),
                request.startAt(),
                request.endAt(),
                request.budgetScoin(),
                request.audienceRoleCodes(),
                request.audienceCampusIds(),
                request.audienceProgramIds(),
                request.audienceSpecializationIds()
        );
        return toResponse(campaignAdminPort.create(adminUserId, command));
    }

    @Transactional
    public AdminCampaignResponse update(UUID adminUserId, UUID campaignId, AdminCampaignUpdateRequest request) {
        AdminCampaignUpdateCommand command = new AdminCampaignUpdateCommand(
                request.name(),
                request.description(),
                request.fundingSource(),
                request.startAt(),
                request.endAt(),
                request.budgetScoin(),
                request.audienceRoleCodes(),
                request.audienceCampusIds(),
                request.audienceProgramIds(),
                request.audienceSpecializationIds()
        );
        return toResponse(campaignAdminPort.update(adminUserId, campaignId, command));
    }

    @Transactional
    public AdminCampaignResponse changeStatus(UUID adminUserId, UUID campaignId, AdminCampaignStatusRequest request) {
        AdminCampaignStatusChangeCommand command = new AdminCampaignStatusChangeCommand(request.status());
        return toResponse(campaignAdminPort.changeStatus(adminUserId, campaignId, command));
    }

    @Transactional(readOnly = true)
    public AdminCampaignAnalyticsResponse getAnalytics(UUID campaignId) {
        AdminCampaignAnalyticsDto dto = campaignAdminPort.getAnalytics(campaignId);
        if (dto == null) {
            return null;
        }
        return new AdminCampaignAnalyticsResponse(
                dto.campaignId(),
                dto.campaignName(),
                dto.status(),
                dto.budgetScoin(),
                dto.budgetUsedScoin(),
                dto.budgetRemainingScoin(),
                dto.budgetBurnRate(),
                dto.totalBookingsCreated(),
                dto.totalCouponRedemptions(),
                dto.totalRevenueScoin(),
                dto.totalCampaignCostScoin(),
                dto.campaignRoiPercent(),
                dto.startAt(),
                dto.endAt(),
                dto.daysActive()
        );
    }

    @Transactional(readOnly = true)
    public List<AdminCampaignBenefitResponse> listBenefits(UUID campaignId) {
        return campaignAdminPort.listBenefits(campaignId).stream().map(this::toBenefitResponse).toList();
    }

    @Transactional
    public AdminCampaignBenefitResponse createBenefit(UUID adminUserId, UUID campaignId, AdminCampaignBenefitCreateRequest request) {
        AdminCampaignBenefitCreateCommand command = new AdminCampaignBenefitCreateCommand(
                request.benefitType(),
                request.creditScoin(),
                request.couponCode(),
                request.couponDiscountType(),
                request.couponDiscountValue(),
                request.couponMaxDiscountScoin(),
                request.couponQuotaTotal(),
                request.couponQuotaPerUser(),
                request.couponMinOrderValueScoin()
        );
        return toBenefitResponse(campaignAdminPort.createBenefit(adminUserId, campaignId, command));
    }

    @Transactional
    public AdminCampaignBenefitResponse updateBenefit(UUID adminUserId, UUID campaignId, UUID benefitId, AdminCampaignBenefitUpdateRequest request) {
        AdminCampaignBenefitUpdateCommand command = new AdminCampaignBenefitUpdateCommand(
                request.benefitType(),
                request.creditScoin(),
                request.couponCode(),
                request.couponDiscountType(),
                request.couponDiscountValue(),
                request.couponMaxDiscountScoin(),
                request.couponQuotaTotal(),
                request.couponQuotaPerUser(),
                request.couponMinOrderValueScoin(),
                request.active()
        );
        return toBenefitResponse(campaignAdminPort.updateBenefit(adminUserId, campaignId, benefitId, command));
    }

    @Transactional
    public void deleteBenefit(UUID adminUserId, UUID campaignId, UUID benefitId) {
        campaignAdminPort.deleteBenefit(adminUserId, campaignId, benefitId);
    }

    private AdminCampaignResponse toResponse(AdminCampaignDto dto) {
        if (dto == null) {
            return null;
        }
        return new AdminCampaignResponse(
                dto.id(),
                dto.name(),
                dto.description(),
                dto.status(),
                dto.fundingSource(),
                dto.startAt(),
                dto.endAt(),
                dto.budgetScoin(),
                dto.budgetUsedScoin(),
                dto.budgetRemainingScoin(),
                dto.audienceRoleCodes(),
                dto.audienceCampusIds(),
                dto.audienceProgramIds(),
                dto.audienceSpecializationIds(),
                dto.benefitCount(),
                dto.totalBookingsCreated(),
                dto.createdAt(),
                dto.updatedAt()
        );
    }

    private AdminCampaignBenefitResponse toBenefitResponse(AdminCampaignBenefitDto dto) {
        if (dto == null) {
            return null;
        }
        return new AdminCampaignBenefitResponse(
                dto.id(),
                dto.campaignId(),
                dto.benefitType(),
                dto.creditScoin(),
                dto.couponCode(),
                dto.couponDiscountType(),
                dto.couponDiscountValue(),
                dto.couponMaxDiscountScoin(),
                dto.couponQuotaTotal(),
                dto.couponQuotaPerUser(),
                dto.couponMinOrderValueScoin(),
                dto.active(),
                dto.createdAt(),
                dto.updatedAt()
        );
    }
}
