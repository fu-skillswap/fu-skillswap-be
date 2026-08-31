package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.dto.request.AdminCampaignBenefitCreateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminCampaignBenefitUpdateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminCampaignCreateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminCampaignListRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminCampaignStatusRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminCampaignUpdateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.AdminCampaignAnalyticsResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.AdminCampaignBenefitResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.AdminCampaignResponse;
import com.fptu.exe.skillswap.modules.payment.domain.Campaign;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefit;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefitType;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.port.CampaignAdminPort;
import com.fptu.exe.skillswap.modules.payment.port.AdminDashboardCampaignOverview;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignBenefitRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignAdminPortImpl implements CampaignAdminPort {

    private static final List<PaymentOrderStatus> EXCLUDED_STATUSES =
            List.of(PaymentOrderStatus.FAILED, PaymentOrderStatus.CANCELLED, PaymentOrderStatus.EXPIRED);

    private final CampaignRepository campaignRepository;
    private final CampaignBenefitRepository campaignBenefitRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CouponRepository couponRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CampaignAdminPort.CampaignView> list(CampaignAdminPort.CampaignListQuery query) {
        AdminCampaignListRequest request = listRequest(query);
        Specification<Campaign> spec = buildSpecification(request);
        Pageable pageable = request.getPageable();
        Page<Campaign> page = campaignRepository.findAll(spec, pageable);

        List<CampaignAdminPort.CampaignView> content = page.getContent().stream()
                .map(item -> view(toResponse(item)))
                .toList();

        return PageResponse.<CampaignAdminPort.CampaignView>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignAdminPort.CampaignView getDetail(UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);
        return view(toResponse(campaign));
    }

    @Override
    @Transactional
    public CampaignAdminPort.CampaignView create(UUID adminUserId, CampaignAdminPort.CreateCampaignCommand command) {
        AdminCampaignCreateRequest request = createRequest(command);
        validateTimeWindow(request.startAt(), request.endAt());

        CampaignStatus initialStatus = CampaignStatus.DRAFT;
        LocalDateTime now = DateTimeUtil.now();
        if (request.startAt() != null && request.startAt().isAfter(now)) {
            initialStatus = CampaignStatus.SCHEDULED;
        }

        Campaign campaign = Campaign.builder()
                .name(request.name().trim())
                .description(request.description())
                .status(initialStatus)
                .fundingSource(request.fundingSource())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .budgetScoin(request.budgetScoin() == null ? 0 : request.budgetScoin())
                .audienceRoleCodes(request.audienceRoleCodes() == null ? new HashSet<>() : new HashSet<>(request.audienceRoleCodes()))
                .audienceCampusIds(request.audienceCampusIds() == null ? new HashSet<>() : new HashSet<>(request.audienceCampusIds()))
                .audienceProgramIds(request.audienceProgramIds() == null ? new HashSet<>() : new HashSet<>(request.audienceProgramIds()))
                .audienceSpecializationIds(request.audienceSpecializationIds() == null ? new HashSet<>() : new HashSet<>(request.audienceSpecializationIds()))
                .build();

        Campaign saved = campaignRepository.save(campaign);
        log.info("Admin {} created campaign {} with status {}", adminUserId, saved.getId(), saved.getStatus());
        return view(toResponse(saved));
    }

    @Override
    @Transactional
    public CampaignAdminPort.CampaignView update(UUID adminUserId, UUID campaignId, CampaignAdminPort.UpdateCampaignCommand command) {
        AdminCampaignUpdateRequest request = updateRequest(command);
        Campaign campaign = findCampaignOrThrow(campaignId);

        if (campaign.getStatus() == CampaignStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể sửa trực tiếp campaign đang ACTIVE. Hãy tạm dừng (PAUSE) trước.");
        }
        if (campaign.getStatus() == CampaignStatus.ENDED || campaign.getStatus() == CampaignStatus.ARCHIVED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể sửa campaign đã kết thúc hoặc lưu trữ.");
        }

        LocalDateTime newStart = request.startAt() != null ? request.startAt() : campaign.getStartAt();
        LocalDateTime newEnd = request.endAt() != null ? request.endAt() : campaign.getEndAt();
        validateTimeWindow(newStart, newEnd);

        if (request.budgetScoin() != null) {
            int usedBudget = getUsedBudget(campaign.getId());
            if (request.budgetScoin() < usedBudget) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Ngân sách mới (" + request.budgetScoin() + " Scoin) phải lớn hơn hoặc bằng ngân sách đã dùng (" + usedBudget + " Scoin)");
            }
            campaign.setBudgetScoin(request.budgetScoin());
        }

        if (request.name() != null && !request.name().isBlank()) {
            campaign.setName(request.name().trim());
        }
        if (request.description() != null) {
            campaign.setDescription(request.description());
        }
        if (request.fundingSource() != null) {
            campaign.setFundingSource(request.fundingSource());
        }
        campaign.setStartAt(request.startAt());
        campaign.setEndAt(request.endAt());

        if (request.audienceRoleCodes() != null) campaign.setAudienceRoleCodes(new HashSet<>(request.audienceRoleCodes()));
        if (request.audienceCampusIds() != null) campaign.setAudienceCampusIds(new HashSet<>(request.audienceCampusIds()));
        if (request.audienceProgramIds() != null) campaign.setAudienceProgramIds(new HashSet<>(request.audienceProgramIds()));
        if (request.audienceSpecializationIds() != null) campaign.setAudienceSpecializationIds(new HashSet<>(request.audienceSpecializationIds()));

        Campaign saved = campaignRepository.save(campaign);
        log.info("Admin {} updated campaign {}", adminUserId, saved.getId());
        return view(toResponse(saved));
    }

    @Override
    @Transactional
    public CampaignAdminPort.CampaignView changeStatus(UUID adminUserId, UUID campaignId, CampaignAdminPort.ChangeCampaignStatusCommand command) {
        AdminCampaignStatusRequest request = new AdminCampaignStatusRequest(enumValue(CampaignStatus.class, command.status()));
        Campaign campaign = findCampaignOrThrow(campaignId);
        CampaignStatus targetStatus = request.status();

        validateStatusTransition(campaign.getStatus(), targetStatus);

        campaign.setStatus(targetStatus);
        Campaign saved = campaignRepository.save(campaign);
        log.info("Admin {} changed campaign {} status from {} to {}", adminUserId, saved.getId(), campaign.getStatus(), targetStatus);
        return view(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignAdminPort.CampaignAnalyticsView getAnalytics(UUID campaignId) {
        Campaign campaign = findCampaignOrThrow(campaignId);
        int budget = campaign.getBudgetScoin() == null ? 0 : campaign.getBudgetScoin();
        int budgetUsed = getUsedBudget(campaign.getId());
        int budgetRemaining = Math.max(0, budget - budgetUsed);
        double burnRate = budget == 0 ? 0.0 : ((double) budgetUsed / budget) * 100.0;

        long totalBookings = paymentOrderRepository.countByCampaignIdAndStatusNotIn(campaignId, EXCLUDED_STATUSES);
        Integer revenueScoinObj = paymentOrderRepository.sumTotalScoinByCampaignIdAndStatusNotIn(campaignId, EXCLUDED_STATUSES);
        int totalRevenueScoin = revenueScoinObj == null ? 0 : revenueScoinObj;

        long couponRedemptions = campaignBenefitRepository.findByCampaignId(campaignId).stream()
                .filter(b -> b.getCouponCode() != null && !b.getCouponCode().isBlank())
                .mapToLong(b -> couponRedemptionRepository.countByCouponId(b.getId()))
                .sum();

        double roi = budgetUsed == 0 ? 0.0 : (((double) totalRevenueScoin - budgetUsed) / budgetUsed) * 100.0;

        long daysActive = 0;
        if (campaign.getStartAt() != null) {
            LocalDateTime end = campaign.getEndAt() != null ? campaign.getEndAt() : DateTimeUtil.now();
            if (end.isAfter(campaign.getStartAt())) {
                daysActive = Duration.between(campaign.getStartAt(), end).toDays();
            }
        }

        return new CampaignAdminPort.CampaignAnalyticsView(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus().name(),
                budget,
                budgetUsed,
                budgetRemaining,
                Math.round(burnRate * 100.0) / 100.0,
                totalBookings,
                couponRedemptions,
                totalRevenueScoin,
                budgetUsed,
                Math.round(roi * 100.0) / 100.0,
                campaign.getStartAt(),
                campaign.getEndAt(),
                daysActive
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAdminPort.CampaignBenefitView> listBenefits(UUID campaignId) {
        findCampaignOrThrow(campaignId);
        return campaignBenefitRepository.findByCampaignId(campaignId).stream()
                .map(item -> benefitView(toBenefitResponse(item)))
                .toList();
    }

    @Override
    @Transactional
    public CampaignAdminPort.CampaignBenefitView createBenefit(UUID adminUserId, UUID campaignId, CampaignAdminPort.CreateCampaignBenefitCommand command) {
        AdminCampaignBenefitCreateRequest request = createBenefitRequest(command);
        Campaign campaign = findCampaignOrThrow(campaignId);

        CampaignBenefit benefit = CampaignBenefit.builder()
                .campaign(campaign)
                .benefitType(request.benefitType())
                .creditScoin(request.creditScoin())
                .couponCode(request.couponCode() == null ? null : request.couponCode().trim().toUpperCase())
                .couponDiscountType(request.couponDiscountType())
                .couponDiscountValue(request.couponDiscountValue())
                .couponMaxDiscountScoin(request.couponMaxDiscountScoin())
                .couponQuotaTotal(request.couponQuotaTotal())
                .couponQuotaPerUser(request.couponQuotaPerUser())
                .couponMinOrderValueScoin(request.couponMinOrderValueScoin())
                .active(true)
                .build();

        CampaignBenefit saved = campaignBenefitRepository.save(benefit);
        log.info("Admin {} created benefit {} for campaign {}", adminUserId, saved.getId(), campaignId);
        return benefitView(toBenefitResponse(saved));
    }

    @Override
    @Transactional
    public CampaignAdminPort.CampaignBenefitView updateBenefit(UUID adminUserId, UUID campaignId, UUID benefitId, CampaignAdminPort.UpdateCampaignBenefitCommand command) {
        AdminCampaignBenefitUpdateRequest request = updateBenefitRequest(command);
        findCampaignOrThrow(campaignId);
        CampaignBenefit benefit = campaignBenefitRepository.findById(benefitId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy benefit"));

        if (!benefit.getCampaign().getId().equals(campaignId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Benefit không thuộc về campaign này");
        }

        if (request.benefitType() != null) benefit.setBenefitType(request.benefitType());
        if (request.creditScoin() != null) benefit.setCreditScoin(request.creditScoin());
        if (request.couponCode() != null) benefit.setCouponCode(request.couponCode().trim().toUpperCase());
        if (request.couponDiscountType() != null) benefit.setCouponDiscountType(request.couponDiscountType());
        if (request.couponDiscountValue() != null) benefit.setCouponDiscountValue(request.couponDiscountValue());
        if (request.couponMaxDiscountScoin() != null) benefit.setCouponMaxDiscountScoin(request.couponMaxDiscountScoin());
        if (request.couponQuotaTotal() != null) benefit.setCouponQuotaTotal(request.couponQuotaTotal());
        if (request.couponQuotaPerUser() != null) benefit.setCouponQuotaPerUser(request.couponQuotaPerUser());
        if (request.couponMinOrderValueScoin() != null) benefit.setCouponMinOrderValueScoin(request.couponMinOrderValueScoin());
        if (request.active() != null) benefit.setActive(request.active());

        CampaignBenefit saved = campaignBenefitRepository.save(benefit);
        log.info("Admin {} updated benefit {} for campaign {}", adminUserId, saved.getId(), campaignId);
        return benefitView(toBenefitResponse(saved));
    }

    @Override
    @Transactional
    public void deleteBenefit(UUID adminUserId, UUID campaignId, UUID benefitId) {
        findCampaignOrThrow(campaignId);
        CampaignBenefit benefit = campaignBenefitRepository.findById(benefitId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy benefit"));

        if (!benefit.getCampaign().getId().equals(campaignId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Benefit không thuộc về campaign này");
        }

        campaignBenefitRepository.delete(benefit);
        log.info("Admin {} deleted benefit {} for campaign {}", adminUserId, benefitId, campaignId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardCampaignOverview getDashboardCampaignOverview() {
        long activeCampaigns = campaignRepository.countByStatus(CampaignStatus.ACTIVE);
        long scheduledCampaigns = campaignRepository.countByStatus(CampaignStatus.SCHEDULED);
        List<Campaign> activeCampaignList = campaignRepository.findByStatus(CampaignStatus.ACTIVE);
        long totalBudgetScoin = activeCampaignList.stream().mapToLong(c -> c.getBudgetScoin() == null ? 0L : c.getBudgetScoin()).sum();
        long totalBudgetUsedScoin = activeCampaignList.stream().mapToLong(c -> {
            Integer used = paymentOrderRepository.sumCampaignCreditByCampaignIdAndStatusNotIn(c.getId(), EXCLUDED_STATUSES);
            return used == null ? 0L : used.longValue();
        }).sum();

        long activeCoupons = couponRepository.countByStatus(CouponStatus.ACTIVE);
        long totalRedemptions = couponRedemptionRepository.count();

        return new AdminDashboardCampaignOverview(
                activeCampaigns,
                scheduledCampaigns,
                totalBudgetScoin,
                totalBudgetUsedScoin,
                activeCoupons,
                totalRedemptions
        );
    }

    private Campaign findCampaignOrThrow(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy campaign"));
    }

    private int getUsedBudget(UUID campaignId) {
        Integer used = paymentOrderRepository.sumCampaignCreditByCampaignIdAndStatusNotIn(campaignId, EXCLUDED_STATUSES);
        return used == null ? 0 : used;
    }

    private void validateTimeWindow(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời điểm kết thúc phải sau thời điểm bắt đầu");
        }
    }

    private void validateStatusTransition(CampaignStatus current, CampaignStatus target) {
        if (current == target) {
            return;
        }
        if (current == CampaignStatus.ARCHIVED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Campaign đã lưu trữ không thể đổi trạng thái");
        }
        boolean allowed = switch (current) {
            case DRAFT -> target == CampaignStatus.SCHEDULED || target == CampaignStatus.ACTIVE || target == CampaignStatus.CANCELLED;
            case SCHEDULED -> target == CampaignStatus.ACTIVE || target == CampaignStatus.DRAFT || target == CampaignStatus.ENDED || target == CampaignStatus.CANCELLED;
            case ACTIVE -> target == CampaignStatus.PAUSED || target == CampaignStatus.ENDED || target == CampaignStatus.EXHAUSTED || target == CampaignStatus.CANCELLED;
            case PAUSED -> target == CampaignStatus.ACTIVE || target == CampaignStatus.ENDED || target == CampaignStatus.CANCELLED;
            case ENDED, EXHAUSTED, CANCELLED -> target == CampaignStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể chuyển trạng thái campaign từ " + current + " sang " + target);
        }
    }

    private AdminCampaignResponse toResponse(Campaign c) {
        int budget = c.getBudgetScoin() == null ? 0 : c.getBudgetScoin();
        int used = getUsedBudget(c.getId());
        int remaining = Math.max(0, budget - used);
        long benefitCount = campaignBenefitRepository.countByCampaignId(c.getId());
        long totalBookings = paymentOrderRepository.countByCampaignIdAndStatusNotIn(c.getId(), EXCLUDED_STATUSES);

        return new AdminCampaignResponse(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getStatus(),
                c.getFundingSource(),
                c.getStartAt(),
                c.getEndAt(),
                budget,
                used,
                remaining,
                c.getAudienceRoleCodes(),
                c.getAudienceCampusIds(),
                c.getAudienceProgramIds(),
                c.getAudienceSpecializationIds(),
                benefitCount,
                totalBookings,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private AdminCampaignBenefitResponse toBenefitResponse(CampaignBenefit b) {
        return new AdminCampaignBenefitResponse(
                b.getId(),
                b.getCampaign().getId(),
                b.getBenefitType(),
                b.getCreditScoin(),
                b.getCouponCode(),
                b.getCouponDiscountType(),
                b.getCouponDiscountValue(),
                b.getCouponMaxDiscountScoin(),
                b.getCouponQuotaTotal(),
                b.getCouponQuotaPerUser(),
                b.getCouponMinOrderValueScoin(),
                b.isActive(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }

    private CampaignAdminPort.CampaignView view(AdminCampaignResponse r) { return new CampaignAdminPort.CampaignView(r.id(), r.name(), r.description(), r.status().name(), r.fundingSource().name(), r.startAt(), r.endAt(), r.budgetScoin(), r.budgetUsedScoin(), r.budgetRemainingScoin(), r.audienceRoleCodes(), r.audienceCampusIds(), r.audienceProgramIds(), r.audienceSpecializationIds(), r.benefitCount(), r.totalBookingsCreated(), r.createdAt(), r.updatedAt()); }
    private CampaignAdminPort.CampaignBenefitView benefitView(AdminCampaignBenefitResponse r) { return new CampaignAdminPort.CampaignBenefitView(r.id(), r.campaignId(), r.benefitType().name(), r.creditScoin(), r.couponCode(), r.couponDiscountType() == null ? null : r.couponDiscountType().name(), r.couponDiscountValue(), r.couponMaxDiscountScoin(), r.couponQuotaTotal(), r.couponQuotaPerUser(), r.couponMinOrderValueScoin(), r.active(), r.createdAt(), r.updatedAt()); }
    private AdminCampaignListRequest listRequest(CampaignAdminPort.CampaignListQuery q) { AdminCampaignListRequest r = new AdminCampaignListRequest(); if(q==null)return r; r.setStatus(q.status()==null?null:enumValue(CampaignStatus.class,q.status())); r.setFundingSource(q.fundingSource()==null?null:enumValue(FundingSource.class,q.fundingSource())); r.setKeyword(q.keyword()); r.setPage(Math.max(0,q.page())); r.setSize(Math.max(1,q.size())); r.setSortBy(q.sortBy()); r.setDirection(q.direction()); return r; }
    private AdminCampaignCreateRequest createRequest(CampaignAdminPort.CreateCampaignCommand c) { return new AdminCampaignCreateRequest(c.name(),c.description(),enumValue(FundingSource.class,c.fundingSource()),c.startAt(),c.endAt(),c.budgetScoin(),c.audienceRoleCodes(),c.audienceCampusIds(),c.audienceProgramIds(),c.audienceSpecializationIds()); }
    private AdminCampaignUpdateRequest updateRequest(CampaignAdminPort.UpdateCampaignCommand c) { return new AdminCampaignUpdateRequest(c.name(),c.description(),c.fundingSource()==null?null:enumValue(FundingSource.class,c.fundingSource()),c.startAt(),c.endAt(),c.budgetScoin(),c.audienceRoleCodes(),c.audienceCampusIds(),c.audienceProgramIds(),c.audienceSpecializationIds()); }
    private AdminCampaignBenefitCreateRequest createBenefitRequest(CampaignAdminPort.CreateCampaignBenefitCommand c) { return new AdminCampaignBenefitCreateRequest(enumValue(CampaignBenefitType.class,c.benefitType()),c.creditScoin(),c.couponCode(),c.couponDiscountType()==null?null:enumValue(CouponDiscountType.class,c.couponDiscountType()),c.couponDiscountValue(),c.couponMaxDiscountScoin(),c.couponQuotaTotal(),c.couponQuotaPerUser(),c.couponMinOrderValueScoin()); }
    private AdminCampaignBenefitUpdateRequest updateBenefitRequest(CampaignAdminPort.UpdateCampaignBenefitCommand c) { return new AdminCampaignBenefitUpdateRequest(c.benefitType()==null?null:enumValue(CampaignBenefitType.class,c.benefitType()),c.creditScoin(),c.couponCode(),c.couponDiscountType()==null?null:enumValue(CouponDiscountType.class,c.couponDiscountType()),c.couponDiscountValue(),c.couponMaxDiscountScoin(),c.couponQuotaTotal(),c.couponQuotaPerUser(),c.couponMinOrderValueScoin(),c.active()); }
    private <E extends Enum<E>> E enumValue(Class<E> type, String value) { try { return Enum.valueOf(type, value); } catch (RuntimeException e) { throw new BaseException(ErrorCode.BAD_REQUEST, "Giá trị campaign không hợp lệ"); } }

    private Specification<Campaign> buildSpecification(AdminCampaignListRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            if (request.getFundingSource() != null) {
                predicates.add(cb.equal(root.get("fundingSource"), request.getFundingSource()));
            }
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                String kw = "%" + request.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), kw));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
