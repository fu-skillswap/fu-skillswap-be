package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.response.UserCouponResponse;
import com.fptu.exe.skillswap.modules.payment.service.CouponService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/coupons")
@Tag(name = "My Coupons", description = "Nhóm API xem danh sách coupon khả dụng của người dùng")
@SecurityRequirement(name = "bearerAuth")
public class MyCouponController {

    private final CouponService couponService;

    @Operation(
            summary = "Xem danh sách coupon khả dụng của tôi",
            description = "Dành cho Mentee xem danh sách coupon còn hiệu lực, số suất còn lại và trạng thái sử dụng của tài khoản."
    )
    @GetMapping
    @PreAuthorize("hasRole('MENTEE')")
    public ResponseEntity<ApiResponse<List<UserCouponResponse>>> getMyCoupons(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ResponseEntity.ok(ApiResponse.success(couponService.getAvailableCouponsForUser(principal.getPublicId())));
    }
}
