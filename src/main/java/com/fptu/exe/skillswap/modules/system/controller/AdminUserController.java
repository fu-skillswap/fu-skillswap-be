package com.fptu.exe.skillswap.modules.system.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.system.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.system.dto.request.BanUserRequest;
import com.fptu.exe.skillswap.modules.system.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.system.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.system.dto.request.UnbanUserRequest;
import com.fptu.exe.skillswap.modules.system.service.AdminUserService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "API quản lý người dùng dành cho Admin")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Lấy danh sách user thường dành cho admin", description = "Chỉ trả về user có role MENTEE hoặc MENTOR, loại bỏ hoàn toàn ADMIN và SYSTEM_ADMIN.")
    @GetMapping
    public ApiResponse<PageResponse<AdminUserListItemResponse>> getUsers(
            @ParameterObject @ModelAttribute AdminUserListRequest request
    ) {
        return ApiResponse.success(adminUserService.getVisibleUsers(request));
    }

    @Operation(summary = "Đình chỉ/Khóa tài khoản của người dùng (Banned)")
    @PostMapping("/{userId}/ban")
    public ApiResponse<SystemUserResponse> banUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody BanUserRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(adminUserService.changeUserStatus(principal.getPublicId(), userId, true, request.getReason()));
    }

    @Operation(summary = "Mở khóa/Kích hoạt lại tài khoản của người dùng (Active)")
    @PostMapping("/{userId}/unban")
    public ApiResponse<SystemUserResponse> unbanUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody UnbanUserRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(adminUserService.changeUserStatus(principal.getPublicId(), userId, false, request.getReason()));
    }
}
