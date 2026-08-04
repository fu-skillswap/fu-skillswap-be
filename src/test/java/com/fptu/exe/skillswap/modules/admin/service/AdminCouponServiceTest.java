package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponResponse;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    @InjectMocks
    private AdminCouponService adminCouponService;

    @Nested
    @DisplayName("Create Coupon Tests")
    class CreateCouponTests {

        @Test
        @DisplayName("Should create coupon successfully with uppercase code")
        void createCoupon_success() {
            UUID adminId = UUID.randomUUID();
            AdminCouponCreateRequest request = new AdminCouponCreateRequest(
                    "welcome50",
                    "Giảm 50%",
                    "Mô tả",
                    CouponDiscountType.PERCENT,
                    50,
                    30000,
                    null, null, 100, 1, 0,
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet()
            );

            Coupon savedCoupon = Coupon.builder()
                    .id(UUID.randomUUID())
                    .code("WELCOME50")
                    .title("Giảm 50%")
                    .discountType(CouponDiscountType.PERCENT)
                    .discountValue(50)
                    .status(CouponStatus.ACTIVE)
                    .build();

            when(couponRepository.existsByCode("WELCOME50")).thenReturn(false);
            when(couponRepository.save(any(Coupon.class))).thenReturn(savedCoupon);

            AdminCouponResponse response = adminCouponService.create(adminId, request);

            assertThat(response).isNotNull();
            assertThat(response.code()).isEqualTo("WELCOME50");
            verify(couponRepository).save(any(Coupon.class));
        }

        @Test
        @DisplayName("Should throw exception when coupon code already exists")
        void createCoupon_duplicateCode() {
            UUID adminId = UUID.randomUUID();
            AdminCouponCreateRequest request = new AdminCouponCreateRequest(
                    "WELCOME50", "Title", "Desc",
                    CouponDiscountType.FIXED, 10000, null, null, null, null, null, null, null, null, null
            );

            when(couponRepository.existsByCode("WELCOME50")).thenReturn(true);

            assertThatThrownBy(() -> adminCouponService.create(adminId, request))
                    .isInstanceOf(BaseException.class)
                    .hasMessageContaining("đã tồn tại trên hệ thống");
        }

        @Test
        @DisplayName("Should throw exception when percent discount > 100")
        void createCoupon_invalidPercent() {
            UUID adminId = UUID.randomUUID();
            AdminCouponCreateRequest request = new AdminCouponCreateRequest(
                    "OVER100", "Title", "Desc",
                    CouponDiscountType.PERCENT, 150, null, null, null, null, null, null, null, null, null
            );

            when(couponRepository.existsByCode("OVER100")).thenReturn(false);

            assertThatThrownBy(() -> adminCouponService.create(adminId, request))
                    .isInstanceOf(BaseException.class)
                    .hasMessageContaining("không được vượt quá 100%");
        }
    }
}
