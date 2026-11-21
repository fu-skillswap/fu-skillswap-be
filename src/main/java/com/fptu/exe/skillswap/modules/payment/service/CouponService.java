package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.payment.dto.response.UserCouponResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptu.exe.skillswap.modules.payment.strategy.DiscountStrategyRegistry;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final Collection<CouponRedemptionStatus> ACTIVE_REDEMPTION_STATUSES =
            List.of(CouponRedemptionStatus.RESERVED, CouponRedemptionStatus.REDEEMED);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final DiscountStrategyRegistry discountStrategyRegistry;
    private final UserQueryPort userQueryPort;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public Coupon resolveCoupon(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        Coupon coupon = couponRepository.findByCodeForUpdate(couponCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy coupon"));
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon hiện không khả dụng");
        }
        return coupon;
    }

    /** Reads coupon state for previews without taking the checkout reservation lock. */
    @Transactional(readOnly = true)
    public Coupon resolveCouponForPreview(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        Coupon coupon = couponRepository.findByCode(couponCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy coupon"));
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon hiện không khả dụng");
        }
        return coupon;
    }

    @Transactional(readOnly = true)
    public void validateApplicable(Coupon coupon, BookingCheckoutSnapshot booking, UUID userId, int grossScoin) {
        if (coupon == null) {
            return;
        }
        if (coupon.getStartAt() != null && timeProvider.nowBusiness().isBefore(coupon.getStartAt())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon chưa bắt đầu");
        }
        if (coupon.getEndAt() != null && timeProvider.nowBusiness().isAfter(coupon.getEndAt())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon đã hết hạn");
        }
        if (coupon.getMinOrderValueScoin() != null && grossScoin < coupon.getMinOrderValueScoin()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Giá trị đơn hàng chưa đạt mức tối thiểu của coupon");
        }
        validateQuotaAvailability(coupon, userId);
        validateUserRoleApplicable(coupon, userId);
        if (!coupon.getApplicableServiceIds().isEmpty()
                && (booking == null || booking.serviceId() == null
                || !coupon.getApplicableServiceIds().contains(booking.serviceId()))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon không áp dụng cho service đã chọn");
        }
        if (!coupon.getApplicableMentorIds().isEmpty()
                && (booking == null
                || booking.mentorUserId() == null
                || !coupon.getApplicableMentorIds().contains(booking.mentorUserId()))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon không áp dụng cho mentor đã chọn");
        }
    }

    @Transactional
    public CouponRedemption reserveCoupon(Coupon coupon, UUID paymentOrderId, UUID redeemerUserId, int discountScoin) {
        if (coupon == null) {
            return null;
        }
        Coupon lockedCoupon = couponRepository.findByIdForUpdate(coupon.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy coupon"));

        CouponRedemption existing = couponRedemptionRepository.findByPaymentOrderId(paymentOrderId).orElse(null);
        boolean alreadyReservedForThisOrder = existing != null
                && existing.getStatus() == CouponRedemptionStatus.RESERVED
                && lockedCoupon.getId().equals(existing.getCouponId());

        if (!alreadyReservedForThisOrder) {
            validateQuotaAvailability(lockedCoupon, redeemerUserId);
            validateUserRoleApplicable(lockedCoupon, redeemerUserId);
        }

        if (existing != null) {
            existing.setCouponId(lockedCoupon.getId());
            existing.setRedeemerUserId(redeemerUserId);
            existing.setStatus(CouponRedemptionStatus.RESERVED);
            existing.setDiscountScoin(discountScoin);
            return couponRedemptionRepository.save(existing);
        }
        return couponRedemptionRepository.save(CouponRedemption.builder()
                .couponId(lockedCoupon.getId())
                .paymentOrderId(paymentOrderId)
                .redeemerUserId(redeemerUserId)
                .status(CouponRedemptionStatus.RESERVED)
                .discountScoin(discountScoin)
                .build());
    }

    @Transactional
    public void markRedeemed(UUID paymentOrderId) {
        couponRedemptionRepository.findByPaymentOrderId(paymentOrderId).ifPresent(redemption -> {
            if (redemption.getStatus() == CouponRedemptionStatus.RESERVED) {
                redemption.setStatus(CouponRedemptionStatus.REDEEMED);
                couponRedemptionRepository.save(redemption);
            }
        });
    }

    @Transactional
    public void voidRedemption(UUID paymentOrderId) {
        couponRedemptionRepository.findByPaymentOrderId(paymentOrderId).ifPresent(redemption -> {
            if (redemption.getStatus() != CouponRedemptionStatus.VOIDED) {
                redemption.setStatus(CouponRedemptionStatus.VOIDED);
                couponRedemptionRepository.save(redemption);
            }
        });
    }

    @Transactional(readOnly = true)
    public int calculateCouponDiscount(Coupon coupon, int grossScoin) {
        if (coupon == null || grossScoin <= 0) {
            return 0;
        }
        if (discountStrategyRegistry != null) {
            return discountStrategyRegistry.calculateDiscount(coupon, grossScoin);
        }
        return 0;
    }

    private void validateQuotaAvailability(Coupon coupon, UUID userId) {
        long totalUsed = couponRedemptionRepository.countByCouponIdAndStatusIn(coupon.getId(), ACTIVE_REDEMPTION_STATUSES);
        if (coupon.getQuotaTotal() != null && totalUsed >= coupon.getQuotaTotal()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon đã hết quota");
        }
        if (userId != null && coupon.getQuotaPerUser() != null) {
            long perUser = couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(
                    coupon.getId(), userId, ACTIVE_REDEMPTION_STATUSES);
            if (perUser >= coupon.getQuotaPerUser()) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã dùng coupon này quá số lần cho phép");
            }
        }
    }

    private void validateUserRoleApplicable(Coupon coupon, UUID userId) {
        if (coupon == null || userId == null || userQueryPort == null) {
            return;
        }
        if ("WELCOME70K".equalsIgnoreCase(coupon.getCode())) {
            UserSummaryRecord user = userQueryPort.findUserSummaryById(userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin người dùng"));
            if (!user.hasRole(RoleCode.MENTEE)) {
                throw new BaseException(ErrorCode.ACCESS_DENIED, "Coupon chào mừng chỉ dành riêng cho tài khoản Mentee");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> getAvailableCouponsForUser(UUID userId) {
        List<Coupon> activeCoupons = couponRepository.findByStatus(CouponStatus.ACTIVE);
        LocalDateTime now = timeProvider.nowBusiness();

        return activeCoupons.stream()
                .map(coupon -> buildUserCouponResponse(coupon, userId, now))
                .sorted(Comparator.comparing(UserCouponResponse::isUsable).reversed()
                        .thenComparing(UserCouponResponse::code))
                .toList();
    }

    private UserCouponResponse buildUserCouponResponse(Coupon coupon, UUID userId, LocalDateTime now) {
        long totalUsed = couponRedemptionRepository.countByCouponIdAndStatusIn(coupon.getId(), ACTIVE_REDEMPTION_STATUSES);
        Integer remainingQuota = coupon.getQuotaTotal() != null
                ? Math.max(0, coupon.getQuotaTotal() - (int) totalUsed)
                : null;

        boolean activeByDate = (coupon.getStartAt() == null || !now.isBefore(coupon.getStartAt()))
                && (coupon.getEndAt() == null || !now.isAfter(coupon.getEndAt()));

        long perUser = (userId != null && coupon.getQuotaPerUser() != null)
                ? couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(coupon.getId(), userId, ACTIVE_REDEMPTION_STATUSES)
                : 0;

        boolean isMentee = true;
        if ("WELCOME70K".equalsIgnoreCase(coupon.getCode()) && userId != null && userQueryPort != null) {
            isMentee = userQueryPort.findUserSummaryById(userId)
                    .map(u -> u.hasRole(RoleCode.MENTEE))
                    .orElse(false);
        }

        boolean isUsable = true;
        String unusableReason = null;

        if (!activeByDate) {
            isUsable = false;
            unusableReason = "Coupon chưa bắt đầu hoặc đã hết hạn";
        } else if (remainingQuota != null && remainingQuota <= 0) {
            isUsable = false;
            unusableReason = "Coupon đã hết lượt sử dụng (quota)";
        } else if (coupon.getQuotaPerUser() != null && perUser >= coupon.getQuotaPerUser()) {
            isUsable = false;
            unusableReason = "Bạn đã sử dụng hết lượt coupon này";
        } else if (!isMentee) {
            isUsable = false;
            unusableReason = "Coupon chào mừng chỉ dành riêng cho tài khoản Mentee";
        }

        return UserCouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .title(coupon.getTitle())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .maxDiscountScoin(coupon.getMaxDiscountScoin())
                .minOrderValueScoin(coupon.getMinOrderValueScoin())
                .quotaTotal(coupon.getQuotaTotal())
                .remainingQuota(remainingQuota)
                .isUsable(isUsable)
                .unusableReason(unusableReason)
                .build();
    }

    /**
     * Evaluates the best active, remaining-quota-available coupon for service discovery / quote preview.
     * Enforces limited system quota and per-user eligibility without reserving a redemption slot.
     */
    @Transactional(readOnly = true)
    public CouponPreviewApplication estimateBestCouponForPreview(
            UUID viewerUserId,
            UUID mentorUserId,
            UUID serviceId,
            int grossScoin
    ) {
        if (grossScoin <= 0) {
            return CouponPreviewApplication.none();
        }
        CouponPreviewContext context = preparePreviewContext(viewerUserId);
        return resolveBestCoupon(context, mentorUserId, serviceId, grossScoin);
    }

    @Transactional(readOnly = true)
    public CouponPreviewContext preparePreviewContext(UUID viewerUserId) {
        List<Coupon> activeCoupons = couponRepository.findByStatus(CouponStatus.ACTIVE);
        if (activeCoupons == null || activeCoupons.isEmpty()) {
            return CouponPreviewContext.empty();
        }

        LocalDateTime now = timeProvider.nowBusiness();
        UserSummaryRecord viewerSummary = (viewerUserId != null && userQueryPort != null)
                ? userQueryPort.findUserSummaryById(viewerUserId).orElse(null)
                : null;

        List<CouponPreviewCandidate> candidates = new ArrayList<>();
        for (Coupon coupon : activeCoupons) {
            if (!isWithinDateWindow(coupon, now)) {
                continue;
            }
            long totalUsed = couponRedemptionRepository.countByCouponIdAndStatusIn(
                    coupon.getId(), ACTIVE_REDEMPTION_STATUSES);
            Integer remainingQuota = coupon.getQuotaTotal() != null
                    ? Math.max(0, coupon.getQuotaTotal() - (int) totalUsed)
                    : null;
            if (remainingQuota != null && remainingQuota <= 0) {
                continue;
            }
            if (viewerUserId != null && coupon.getQuotaPerUser() != null) {
                long perUserUsed = couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(
                        coupon.getId(), viewerUserId, ACTIVE_REDEMPTION_STATUSES);
                if (perUserUsed >= coupon.getQuotaPerUser()) {
                    continue;
                }
            }
            if ("WELCOME70K".equalsIgnoreCase(coupon.getCode()) && viewerUserId != null) {
                if (viewerSummary == null || !viewerSummary.hasRole(RoleCode.MENTEE)) {
                    continue;
                }
            }
            Set<UUID> serviceIds = coupon.getApplicableServiceIds() == null
                    ? Set.of()
                    : Set.copyOf(coupon.getApplicableServiceIds());
            Set<UUID> mentorIds = coupon.getApplicableMentorIds() == null
                    ? Set.of()
                    : Set.copyOf(coupon.getApplicableMentorIds());
            candidates.add(new CouponPreviewCandidate(coupon, remainingQuota, serviceIds, mentorIds));
        }
        return new CouponPreviewContext(candidates);
    }

    @Transactional(readOnly = true)
    public CouponPreviewApplication resolveBestCoupon(
            CouponPreviewContext context,
            UUID mentorUserId,
            UUID serviceId,
            int grossScoin
    ) {
        if (context == null || context.candidates().isEmpty() || grossScoin <= 0) {
            return CouponPreviewApplication.none();
        }
        CouponPreviewApplication best = CouponPreviewApplication.none();
        for (CouponPreviewCandidate candidate : context.candidates()) {
            Coupon coupon = candidate.coupon();
            if (coupon.getMinOrderValueScoin() != null && grossScoin < coupon.getMinOrderValueScoin()) {
                continue;
            }
            if (!candidate.applicableServiceIds().isEmpty()
                    && (serviceId == null || !candidate.applicableServiceIds().contains(serviceId))) {
                continue;
            }
            if (!candidate.applicableMentorIds().isEmpty()
                    && (mentorUserId == null || !candidate.applicableMentorIds().contains(mentorUserId))) {
                continue;
            }
            int discountScoin = Math.max(0, Math.min(grossScoin, calculateCouponDiscount(coupon, grossScoin)));
            if (discountScoin <= 0) {
                continue;
            }
            boolean better = discountScoin > best.discountScoin()
                    || (discountScoin == best.discountScoin()
                    && best.couponCode() != null
                    && coupon.getCode() != null
                    && coupon.getCode().compareToIgnoreCase(best.couponCode()) < 0);
            if (better) {
                best = new CouponPreviewApplication(
                        coupon.getId(),
                        coupon.getCode(),
                        coupon.getTitle(),
                        discountScoin,
                        candidate.remainingQuota()
                );
            }
        }
        return best;
    }

    private boolean isWithinDateWindow(Coupon coupon, LocalDateTime now) {
        if (coupon.getStartAt() != null && now.isBefore(coupon.getStartAt())) {
            return false;
        }
        return coupon.getEndAt() == null || !now.isAfter(coupon.getEndAt());
    }

    public record CouponPreviewApplication(
            UUID couponId,
            String couponCode,
            String couponTitle,
            int discountScoin,
            Integer remainingQuota
    ) {
        public static CouponPreviewApplication none() {
            return new CouponPreviewApplication(null, null, null, 0, null);
        }

        public boolean applied() {
            return couponId != null && discountScoin > 0;
        }
    }

    public record CouponPreviewContext(List<CouponPreviewCandidate> candidates) {
        public static CouponPreviewContext empty() {
            return new CouponPreviewContext(List.of());
        }
    }

    public record CouponPreviewCandidate(
            Coupon coupon,
            Integer remainingQuota,
            Set<UUID> applicableServiceIds,
            Set<UUID> applicableMentorIds
    ) {
    }
}
