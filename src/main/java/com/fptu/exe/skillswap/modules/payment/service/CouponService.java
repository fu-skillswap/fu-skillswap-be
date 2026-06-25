package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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

    @Transactional(readOnly = true)
    public Coupon resolveCoupon(String couponCode) {
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
    public void validateApplicable(Coupon coupon, Booking booking, UUID userId, int grossScoin) {
        if (coupon == null) {
            return;
        }
        if (coupon.getStartAt() != null && DateTimeUtil.now().isBefore(coupon.getStartAt())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon chưa bắt đầu");
        }
        if (coupon.getEndAt() != null && DateTimeUtil.now().isAfter(coupon.getEndAt())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon đã hết hạn");
        }
        if (coupon.getMinOrderValueScoin() != null && grossScoin < coupon.getMinOrderValueScoin()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Giá trị đơn hàng chưa đạt mức tối thiểu của coupon");
        }
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
        if (!coupon.getApplicableServiceIds().isEmpty()
                && (booking == null || booking.getService() == null
                || booking.getService().getId() == null
                || !coupon.getApplicableServiceIds().contains(booking.getService().getId()))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon không áp dụng cho service đã chọn");
        }
        if (!coupon.getApplicableMentorIds().isEmpty()
                && (booking == null
                || booking.getMentorProfile() == null
                || booking.getMentorProfile().getUserId() == null
                || !coupon.getApplicableMentorIds().contains(booking.getMentorProfile().getUserId()))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Coupon không áp dụng cho mentor đã chọn");
        }
    }

    @Transactional
    public CouponRedemption reserveCoupon(Coupon coupon, UUID paymentOrderId, UUID redeemerUserId, int discountScoin) {
        if (coupon == null) {
            return null;
        }
        return couponRedemptionRepository.save(CouponRedemption.builder()
                .couponId(coupon.getId())
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
        if (coupon == null) {
            return 0;
        }
        return switch (coupon.getDiscountType()) {
            case FIXED -> Math.min(Math.max(0, coupon.getDiscountValue() == null ? 0 : coupon.getDiscountValue()), grossScoin);
            case PERCENT -> {
                int percent = Math.max(0, coupon.getDiscountValue() == null ? 0 : coupon.getDiscountValue());
                int discount = (grossScoin * percent) / 100;
                if (coupon.getMaxDiscountScoin() != null) {
                    discount = Math.min(discount, Math.max(0, coupon.getMaxDiscountScoin()));
                }
                yield Math.min(discount, grossScoin);
            }
        };
    }
}
