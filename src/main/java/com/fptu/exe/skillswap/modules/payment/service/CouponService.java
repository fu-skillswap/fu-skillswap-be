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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
}
