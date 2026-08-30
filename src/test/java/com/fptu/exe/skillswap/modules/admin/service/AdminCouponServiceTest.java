package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponResponse;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.port.CouponAdminPort;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponCreateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponDto;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponServiceTest {

    @Mock
    private CouponAdminPort couponAdminPort;

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
                    Collections.emptySet()
            );

            AdminCouponDto mockDto = new AdminCouponDto(
                    UUID.randomUUID(),
                    "WELCOME50",
                    "Giảm 50%",
                    "Mô tả",
                    CouponDiscountType.PERCENT,
                    50,
                    30000,
                    CouponStatus.ACTIVE,
                    null,
                    null,
                    100,
                    1,
                    0,
                    Collections.emptySet(),
                    Collections.emptySet(),
                    0,
                    0,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );

            when(couponAdminPort.create(eq(adminId), any(AdminCouponCreateCommand.class))).thenReturn(mockDto);

            AdminCouponResponse response = adminCouponService.create(adminId, request);

            assertThat(response).isNotNull();
            assertThat(response.code()).isEqualTo("WELCOME50");
            verify(couponAdminPort).create(eq(adminId), any(AdminCouponCreateCommand.class));
        }

        @Test
        @DisplayName("Should throw exception when coupon code already exists")
        void createCoupon_duplicateCode() {
            UUID adminId = UUID.randomUUID();
            AdminCouponCreateRequest request = new AdminCouponCreateRequest(
                    "WELCOME50", "Title", "Desc",
                    CouponDiscountType.FIXED, 10000, null, null, null, null, null, null, null, null
            );

            when(couponAdminPort.create(eq(adminId), any(AdminCouponCreateCommand.class)))
                    .thenThrow(new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mã giảm giá đã tồn tại trên hệ thống"));

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
                    CouponDiscountType.PERCENT, 150, null, null, null, null, null, null, null, null
            );

            when(couponAdminPort.create(eq(adminId), any(AdminCouponCreateCommand.class)))
                    .thenThrow(new BaseException(ErrorCode.BAD_REQUEST, "Phần trăm giảm giá không được vượt quá 100%"));

            assertThatThrownBy(() -> adminCouponService.create(adminId, request))
                    .isInstanceOf(BaseException.class)
                    .hasMessageContaining("không được vượt quá 100%");
        }
    }
}
