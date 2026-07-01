package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    @InjectMocks
    private CouponService couponService;

    @Test
    void resolveCoupon_shouldLockCouponByCode() {
        Coupon coupon = Coupon.builder()
                .id(UUID.randomUUID())
                .code("SAVE50")
                .status(CouponStatus.ACTIVE)
                .discountType(CouponDiscountType.FIXED)
                .discountValue(50)
                .build();

        when(couponRepository.findByCodeForUpdate("SAVE50")).thenReturn(Optional.of(coupon));

        Coupon resolved = couponService.resolveCoupon("save50");

        assertEquals(coupon.getId(), resolved.getId());
        verify(couponRepository).findByCodeForUpdate("SAVE50");
    }

    @Test
    void reserveCoupon_shouldLockCouponAndReserveWhenQuotaStillAvailable() {
        UUID couponId = UUID.randomUUID();
        UUID paymentOrderId = UUID.randomUUID();
        UUID redeemerUserId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("SAVE50")
                .status(CouponStatus.ACTIVE)
                .quotaTotal(2)
                .quotaPerUser(1)
                .discountType(CouponDiscountType.FIXED)
                .discountValue(50)
                .build();

        when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(coupon));
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(couponId), any())).thenReturn(1L);
        when(couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(eq(couponId), eq(redeemerUserId), any()))
                .thenReturn(0L);
        when(couponRedemptionRepository.findByPaymentOrderId(paymentOrderId)).thenReturn(Optional.empty());
        when(couponRedemptionRepository.save(any(CouponRedemption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponRedemption redemption = couponService.reserveCoupon(coupon, paymentOrderId, redeemerUserId, 50);

        assertEquals(couponId, redemption.getCouponId());
        assertEquals(paymentOrderId, redemption.getPaymentOrderId());
        assertEquals(CouponRedemptionStatus.RESERVED, redemption.getStatus());
        verify(couponRepository).findByIdForUpdate(couponId);
    }

    @Test
    void reserveCoupon_shouldRejectWhenQuotaAlreadyExhaustedUnderLock() {
        UUID couponId = UUID.randomUUID();
        UUID paymentOrderId = UUID.randomUUID();
        UUID redeemerUserId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("SAVE50")
                .status(CouponStatus.ACTIVE)
                .quotaTotal(1)
                .discountType(CouponDiscountType.FIXED)
                .discountValue(50)
                .build();

        when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(coupon));
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(couponId), any())).thenReturn(1L);

        BaseException exception = assertThrows(BaseException.class,
                () -> couponService.reserveCoupon(coupon, paymentOrderId, redeemerUserId, 50));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        assertEquals("Coupon đã hết quota", exception.getMessage());
    }
}
