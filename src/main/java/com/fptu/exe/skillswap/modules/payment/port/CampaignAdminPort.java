package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CampaignAdminPort {
    PageResponse<CampaignView> list(CampaignListQuery query);
    CampaignView getDetail(UUID campaignId);
    CampaignView create(UUID adminUserId, CreateCampaignCommand command);
    CampaignView update(UUID adminUserId, UUID campaignId, UpdateCampaignCommand command);
    CampaignView changeStatus(UUID adminUserId, UUID campaignId, ChangeCampaignStatusCommand command);
    CampaignAnalyticsView getAnalytics(UUID campaignId);
    List<CampaignBenefitView> listBenefits(UUID campaignId);
    CampaignBenefitView createBenefit(UUID adminUserId, UUID campaignId, CreateCampaignBenefitCommand command);
    CampaignBenefitView updateBenefit(UUID adminUserId, UUID campaignId, UUID benefitId, UpdateCampaignBenefitCommand command);
    void deleteBenefit(UUID adminUserId, UUID campaignId, UUID benefitId);
    AdminDashboardCampaignOverview getDashboardCampaignOverview();

    record CampaignListQuery(String status, String fundingSource, String keyword, int page, int size, String sortBy, String direction) { }
    record CreateCampaignCommand(@NotBlank @Size(max = 150) String name, String description, @NotNull String fundingSource, LocalDateTime startAt, LocalDateTime endAt, @NotNull @Min(0) Integer budgetScoin, Set<String> audienceRoleCodes, Set<UUID> audienceCampusIds, Set<UUID> audienceProgramIds, Set<UUID> audienceSpecializationIds) { }
    record UpdateCampaignCommand(@Size(max = 150) String name, String description, String fundingSource, LocalDateTime startAt, LocalDateTime endAt, @Min(0) Integer budgetScoin, Set<String> audienceRoleCodes, Set<UUID> audienceCampusIds, Set<UUID> audienceProgramIds, Set<UUID> audienceSpecializationIds) { }
    record ChangeCampaignStatusCommand(@NotNull String status) { }
    record CampaignView(UUID id, String name, String description, String status, String fundingSource, LocalDateTime startAt, LocalDateTime endAt, Integer budgetScoin, Integer budgetUsedScoin, Integer budgetRemainingScoin, Set<String> audienceRoleCodes, Set<UUID> audienceCampusIds, Set<UUID> audienceProgramIds, Set<UUID> audienceSpecializationIds, long benefitCount, long totalBookingsCreated, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record CampaignAnalyticsView(UUID campaignId, String campaignName, String status, int budgetScoin, int budgetUsedScoin, int budgetRemainingScoin, double budgetBurnRate, long totalBookingsCreated, long totalCouponRedemptions, int totalRevenueScoin, int totalCampaignCostScoin, double campaignRoiPercent, LocalDateTime startAt, LocalDateTime endAt, long daysActive) { }
    record CreateCampaignBenefitCommand(@NotNull String benefitType, @Min(0) Integer creditScoin, String couponCode, String couponDiscountType, @Min(0) Integer couponDiscountValue, @Min(0) Integer couponMaxDiscountScoin, @Min(0) Integer couponQuotaTotal, @Min(0) Integer couponQuotaPerUser, @Min(0) Integer couponMinOrderValueScoin) { }
    record UpdateCampaignBenefitCommand(String benefitType, @Min(0) Integer creditScoin, String couponCode, String couponDiscountType, @Min(0) Integer couponDiscountValue, @Min(0) Integer couponMaxDiscountScoin, @Min(0) Integer couponQuotaTotal, @Min(0) Integer couponQuotaPerUser, @Min(0) Integer couponMinOrderValueScoin, Boolean active) { }
    record CampaignBenefitView(UUID id, UUID campaignId, String benefitType, Integer creditScoin, String couponCode, String couponDiscountType, Integer couponDiscountValue, Integer couponMaxDiscountScoin, Integer couponQuotaTotal, Integer couponQuotaPerUser, Integer couponMinOrderValueScoin, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) { }
}
