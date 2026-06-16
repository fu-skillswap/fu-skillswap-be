package com.fptu.exe.skillswap.modules.system.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.system.dto.request.AdminRoleChangeRequest;
import com.fptu.exe.skillswap.modules.system.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.system.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.system.service.SystemUserRoleService;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
@Tag(name = "System User Roles", description = "API quản trị quyền vận hành hệ thống dành cho System Admin")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemUserRoleController {

    private final SystemUserRoleService systemUserRoleService;

    @Operation(summary = "Cấp quyền ADMIN cho user theo email")
    @PostMapping("/admin-role/grant")
    public ApiResponse<AdminUserResponse> grantAdminRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminRoleChangeRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(systemUserRoleService.grantAdminRole(principal.getPublicId(), request.email()));
    }

    @Operation(summary = "Thu hồi quyền ADMIN của user theo email")
    @PostMapping("/admin-role/revoke")
    public ApiResponse<AdminUserResponse> revokeAdminRole(@Valid @RequestBody AdminRoleChangeRequest request) {
        return ApiResponse.success(systemUserRoleService.revokeAdminRole(request.email()));
    }

    @Operation(summary = "Xem danh sách user đang có quyền ADMIN")
    @GetMapping("/admins")
    public ApiResponse<PageResponse<AdminUserResponse>> getAdminUsers(@ParameterObject @ModelAttribute BasePageRequest pageRequest) {
        return ApiResponse.success(systemUserRoleService.getAdminUsers(pageRequest));
    }

    @Operation(summary = "Lấy danh sách toàn bộ user trong hệ thống")
    @GetMapping
    public ApiResponse<PageResponse<SystemUserResponse>> getAllUsers(@ParameterObject @ModelAttribute BasePageRequest pageRequest) {
        return ApiResponse.success(systemUserRoleService.getAllUsers(pageRequest));
    }
}
