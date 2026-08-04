package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponRedemptionResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminCouponService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "Admin - Coupons", description = "Nhóm API cho admin quản lý danh mục mã giảm giá (Coupons), quy định sử dụng và lịch sử đổi mã (Redemptions).")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @GetMapping
    @Operation(summary = "Lấy danh sách coupon (paginated & filtered)", description = "Hỗ trợ lọc theo status, discountType, keyword (code hoặc title).")
    public ApiResponse<PageResponse<AdminCouponResponse>> list(
            @ParameterObject @ModelAttribute AdminCouponListRequest request
    ) {
        return ApiResponse.success(adminCouponService.list(request));
    }

    @GetMapping("/{couponId}")
    @Operation(summary = "Lấy chi tiết một coupon theo ID")
    public ApiResponse<AdminCouponResponse> getDetail(@PathVariable UUID couponId) {
        return ApiResponse.success(adminCouponService.getDetail(couponId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo coupon mới cho hệ thống")
    public ApiResponse<AdminCouponResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminCouponCreateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(adminCouponService.create(principal.getPublicId(), request));
    }

    @PutMapping("/{couponId}")
    @Operation(summary = "Cập nhật thông tin coupon")
    public ApiResponse<AdminCouponResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID couponId,
            @Valid @RequestBody AdminCouponUpdateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(adminCouponService.update(principal.getPublicId(), couponId, request));
    }

    @PatchMapping("/{couponId}/status")
    @Operation(summary = "Chuyển trạng thái coupon (ACTIVE, INACTIVE, SUSPENDED)")
    public ApiResponse<AdminCouponResponse> changeStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID couponId,
            @Valid @RequestBody AdminCouponStatusRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(adminCouponService.changeStatus(principal.getPublicId(), couponId, request));
    }

    @GetMapping("/{couponId}/redemptions")
    @Operation(summary = "Lấy danh sách các lượt đổi mã (Redemption history) của coupon")
    public ApiResponse<PageResponse<AdminCouponRedemptionResponse>> getRedemptions(
            @PathVariable UUID couponId,
            @ParameterObject Pageable pageable
    ) {
        return ApiResponse.success(adminCouponService.getRedemptions(couponId, pageable));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
