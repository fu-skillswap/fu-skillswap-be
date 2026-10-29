package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.modules.payment.dto.response.UserCouponResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private UserQueryPort userQueryPort;

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

    @Test
    void validateApplicable_welcomeCoupon_nonMentee_shouldThrowForbidden() {
        UUID userId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(UUID.randomUUID())
                .code("WELCOME70K")
                .status(CouponStatus.ACTIVE)
                .quotaTotal(60)
                .quotaPerUser(1)
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .build();

        UserSummaryRecord nonMentee = new UserSummaryRecord(
                userId, "admin@test.com", "Admin User", null,
                Set.of(RoleCode.SYSTEM_ADMIN), "ACTIVE", true
        );
        when(userQueryPort.findUserSummaryById(userId)).thenReturn(Optional.of(nonMentee));

        BaseException exception = assertThrows(BaseException.class,
                () -> couponService.validateApplicable(coupon, null, userId, 100000));

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        assertEquals("Coupon chào mừng chỉ dành riêng cho tài khoản Mentee", exception.getMessage());
    }

    @Test
    void validateApplicable_welcomeCoupon_mentee_shouldPass() {
        UUID userId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(UUID.randomUUID())
                .code("WELCOME70K")
                .status(CouponStatus.ACTIVE)
                .quotaTotal(60)
                .quotaPerUser(1)
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .build();

        UserSummaryRecord mentee = new UserSummaryRecord(
                userId, "student@fpt.edu.vn", "Student A", null,
                Set.of(RoleCode.MENTEE), "ACTIVE", true
        );
        when(userQueryPort.findUserSummaryById(userId)).thenReturn(Optional.of(mentee));
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(coupon.getId()), any())).thenReturn(10L);
        when(couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(eq(coupon.getId()), eq(userId), any())).thenReturn(0L);

        couponService.validateApplicable(coupon, null, userId, 100000);
        verify(userQueryPort).findUserSummaryById(userId);
    }

    @Test
    void reserveCoupon_totalQuota60Exceeded_shouldThrowConflict() {
        UUID couponId = UUID.randomUUID();
        UUID paymentOrderId = UUID.randomUUID();
        UUID redeemerUserId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("WELCOME70K")
                .status(CouponStatus.ACTIVE)
                .quotaTotal(60)
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .build();

        when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(coupon));
        when(couponRedemptionRepository.findByPaymentOrderId(paymentOrderId)).thenReturn(Optional.empty());
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(couponId), any())).thenReturn(60L);

        BaseException exception = assertThrows(BaseException.class,
                () -> couponService.reserveCoupon(coupon, paymentOrderId, redeemerUserId, 70000));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        assertEquals("Coupon đã hết quota", exception.getMessage());
    }

    @Test
    void reserveCoupon_existingReservedOrder_shouldNotCheckQuotaAgain() {
        UUID couponId = UUID.randomUUID();
        UUID paymentOrderId = UUID.randomUUID();
        UUID redeemerUserId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("WELCOME70K")
                .status(CouponStatus.ACTIVE)
                .quotaTotal(60)
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .build();

        CouponRedemption existing = CouponRedemption.builder()
                .couponId(couponId)
                .paymentOrderId(paymentOrderId)
                .redeemerUserId(redeemerUserId)
                .status(CouponRedemptionStatus.RESERVED)
                .discountScoin(70000)
                .build();

        when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(coupon));
        when(couponRedemptionRepository.findByPaymentOrderId(paymentOrderId)).thenReturn(Optional.of(existing));
        when(couponRedemptionRepository.save(any(CouponRedemption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponRedemption redemption = couponService.reserveCoupon(coupon, paymentOrderId, redeemerUserId, 70000);

        assertEquals(CouponRedemptionStatus.RESERVED, redemption.getStatus());
        verify(couponRedemptionRepository).save(existing);
    }

    @Test
    void markRedeemed_and_voidRedemption_lifecycle() {
        UUID paymentOrderId = UUID.randomUUID();
        CouponRedemption redemption = CouponRedemption.builder()
                .id(UUID.randomUUID())
                .paymentOrderId(paymentOrderId)
                .status(CouponRedemptionStatus.RESERVED)
                .discountScoin(70000)
                .build();

        when(couponRedemptionRepository.findByPaymentOrderId(paymentOrderId)).thenReturn(Optional.of(redemption));
        when(couponRedemptionRepository.save(any(CouponRedemption.class))).thenAnswer(invocation -> invocation.getArgument(0));

        couponService.markRedeemed(paymentOrderId);
        assertEquals(CouponRedemptionStatus.REDEEMED, redemption.getStatus());

        couponService.voidRedemption(paymentOrderId);
        assertEquals(CouponRedemptionStatus.VOIDED, redemption.getStatus());
    }

    @Test
    void getAvailableCouponsForUser_newMentee_shouldBeUsableWithFullRemainingQuota() {
        UUID userId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("WELCOME70K")
                .title("Mã chào mừng")
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .quotaTotal(60)
                .quotaPerUser(1)
                .status(CouponStatus.ACTIVE)
                .build();

        UserSummaryRecord mentee = new UserSummaryRecord(
                userId, "mentee@test.com", "Mentee", null,
                Set.of(RoleCode.MENTEE), "ACTIVE", true
        );
        when(userQueryPort.findUserSummaryById(userId)).thenReturn(Optional.of(mentee));
        when(couponRepository.findByStatus(CouponStatus.ACTIVE)).thenReturn(List.of(coupon));
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(couponId), any())).thenReturn(0L);
        when(couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(eq(couponId), eq(userId), any())).thenReturn(0L);

        List<UserCouponResponse> responses = couponService.getAvailableCouponsForUser(userId);

        assertEquals(1, responses.size());
        UserCouponResponse resp = responses.get(0);
        assertEquals("WELCOME70K", resp.code());
        assertEquals(60, resp.quotaTotal());
        assertEquals(60, resp.remainingQuota());
        assertTrue(resp.isUsable());
        assertNull(resp.unusableReason());
    }

    @Test
    void getAvailableCouponsForUser_menteeAlreadyUsed_shouldNotBeUsable() {
        UUID userId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("WELCOME70K")
                .title("Mã chào mừng")
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .quotaTotal(60)
                .quotaPerUser(1)
                .status(CouponStatus.ACTIVE)
                .build();

        UserSummaryRecord mentee = new UserSummaryRecord(
                userId, "mentee@test.com", "Mentee", null,
                Set.of(RoleCode.MENTEE), "ACTIVE", true
        );
        when(userQueryPort.findUserSummaryById(userId)).thenReturn(Optional.of(mentee));
        when(couponRepository.findByStatus(CouponStatus.ACTIVE)).thenReturn(List.of(coupon));
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(couponId), any())).thenReturn(5L);
        when(couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(eq(couponId), eq(userId), any())).thenReturn(1L);

        List<UserCouponResponse> responses = couponService.getAvailableCouponsForUser(userId);

        assertEquals(1, responses.size());
        UserCouponResponse resp = responses.get(0);
        assertEquals(55, resp.remainingQuota());
        assertFalse(resp.isUsable());
        assertEquals("Bạn đã sử dụng hết lượt coupon này", resp.unusableReason());
    }

    @Test
    void getAvailableCouponsForUser_quotaExhausted_shouldNotBeUsable() {
        UUID userId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("WELCOME70K")
                .title("Mã chào mừng")
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .quotaTotal(60)
                .quotaPerUser(1)
                .status(CouponStatus.ACTIVE)
                .build();

        when(couponRepository.findByStatus(CouponStatus.ACTIVE)).thenReturn(List.of(coupon));
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(couponId), any())).thenReturn(60L);
        when(couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(eq(couponId), eq(userId), any())).thenReturn(0L);

        List<UserCouponResponse> responses = couponService.getAvailableCouponsForUser(userId);

        assertEquals(1, responses.size());
        UserCouponResponse resp = responses.get(0);
        assertEquals(0, resp.remainingQuota());
        assertFalse(resp.isUsable());
        assertEquals("Coupon đã hết lượt sử dụng (quota)", resp.unusableReason());
    }

    @Test
    void getAvailableCouponsForUser_nonMentee_shouldNotBeUsable() {
        UUID userId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .id(couponId)
                .code("WELCOME70K")
                .title("Mã chào mừng")
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .quotaTotal(60)
                .quotaPerUser(1)
                .status(CouponStatus.ACTIVE)
                .build();

        UserSummaryRecord admin = new UserSummaryRecord(
                userId, "admin@test.com", "Admin", null,
                Set.of(RoleCode.ADMIN), "ACTIVE", true
        );
        when(userQueryPort.findUserSummaryById(userId)).thenReturn(Optional.of(admin));
        when(couponRepository.findByStatus(CouponStatus.ACTIVE)).thenReturn(List.of(coupon));
        when(couponRedemptionRepository.countByCouponIdAndStatusIn(eq(couponId), any())).thenReturn(10L);
        when(couponRedemptionRepository.countByCouponIdAndRedeemerUserIdAndStatusIn(eq(couponId), eq(userId), any())).thenReturn(0L);

        List<UserCouponResponse> responses = couponService.getAvailableCouponsForUser(userId);

        assertEquals(1, responses.size());
        UserCouponResponse resp = responses.get(0);
        assertFalse(resp.isUsable());
        assertEquals("Coupon chào mừng chỉ dành riêng cho tài khoản Mentee", resp.unusableReason());
    }
}
