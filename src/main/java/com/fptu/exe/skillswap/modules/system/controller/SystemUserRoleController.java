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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "System Admin - Roles", description = "Nhóm API cấp hệ thống để cấp/thu hồi quyền ADMIN và xem danh sách tài khoản quản trị. Grant ADMIN sẽ gỡ MENTEE/MENTOR để tài khoản thành admin-only; revoke ADMIN sẽ trả user về MENTEE mặc định. Chỉ FE dành cho SYSTEM_ADMIN mới nên dùng nhóm API này.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemUserRoleController {

    private final SystemUserRoleService systemUserRoleService;

    @Operation(
            summary = "Cấp quyền admin",
            description = "Cấp quyền ADMIN cho một user đã tồn tại theo email. Khi cấp thành công, backend gỡ MENTEE và MENTOR để tài khoản trở thành admin-only, tránh tiếp tục dùng các API self-service/mentoring dành cho user thường. FE nội bộ chỉ dùng API này trong màn hình role management dành cho SYSTEM_ADMIN khi cần nâng quyền vận hành cho một tài khoản."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cấp quyền thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền system admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy user")
    })
    @PostMapping("/admin-role/grant")
    public ApiResponse<AdminUserResponse> grantAdminRole(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminRoleChangeRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(systemUserRoleService.grantAdminRole(principal.getPublicId(), request.email()));
    }

    @Operation(
            summary = "Thu hồi quyền admin",
            description = "Thu hồi quyền ADMIN của một user đã tồn tại theo email. Khi thu hồi thành công, backend gỡ ADMIN, gỡ MENTOR nếu còn dữ liệu cũ, và gán lại MENTEE mặc định. FE nội bộ chỉ dùng API này trong màn hình role management dành cho SYSTEM_ADMIN khi cần gỡ quyền vận hành của một tài khoản."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thu hồi quyền thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền system admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy user")
    })
    @PostMapping("/admin-role/revoke")
    public ApiResponse<AdminUserResponse> revokeAdminRole(@Valid @RequestBody AdminRoleChangeRequest request) {
        return ApiResponse.success(systemUserRoleService.revokeAdminRole(request.email()));
    }

    @Operation(
            summary = "Lấy danh sách admin users",
            description = "Trả về danh sách user hiện đang có quyền ADMIN. FE dùng để hiển thị danh sách tài khoản admin nội bộ và kiểm tra lại kết quả sau khi grant/revoke role."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền system admin")
    })
    @GetMapping("/admins")
    public ApiResponse<PageResponse<AdminUserResponse>> getAdminUsers(@ParameterObject @ModelAttribute BasePageRequest pageRequest) {
        return ApiResponse.success(systemUserRoleService.getAdminUsers(pageRequest));
    }

    @Operation(
            summary = "Lấy danh sách toàn bộ system users",
            description = "Trả về toàn bộ user trong hệ thống phục vụ quản lý role ở cấp system. FE dùng trong các màn hình SYSTEM_ADMIN khi cần phạm vi dữ liệu rộng hơn danh sách visible users của admin vận hành."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền system admin")
    })
    @GetMapping
    public ApiResponse<PageResponse<SystemUserResponse>> getAllUsers(@ParameterObject @ModelAttribute BasePageRequest pageRequest) {
        return ApiResponse.success(systemUserRoleService.getAllUsers(pageRequest));
    }
}
