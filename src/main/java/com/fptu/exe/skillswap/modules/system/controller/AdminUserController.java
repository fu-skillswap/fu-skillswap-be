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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Admin - Users", description = "Nhóm API vận hành nội bộ để xem danh sách user visible và thay đổi trạng thái tài khoản như ban hoặc unban. FE admin dùng trong các màn moderation user.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "Lấy danh sách visible users",
            description = "Trả về danh sách user không phải admin mà admin vận hành được phép nhìn thấy. FE admin dùng cho các màn moderation với keyword, role, status, pagination và sorting; tài khoản ADMIN và SYSTEM_ADMIN được loại khỏi danh sách này theo thiết kế."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách người dùng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping
    public ApiResponse<PageResponse<AdminUserListItemResponse>> getUsers(
            @ParameterObject @ModelAttribute AdminUserListRequest request
    ) {
        return ApiResponse.success(adminUserService.getVisibleUsers(request));
    }

    @Operation(summary = "Khóa user", description = "Chuyển trạng thái tài khoản của một visible user sang banned. FE admin dùng trong flow moderation khi cần chặn user sử dụng hệ thống và phải lưu lại lý do thao tác.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cấm người dùng thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @PostMapping("/{userId}/ban")
    public ApiResponse<SystemUserResponse> banUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody BanUserRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(adminUserService.changeUserStatus(principal.getPublicId(), userId, true, request.getReason()));
    }

    @Operation(summary = "Mở khóa user", description = "Khôi phục một tài khoản banned về trạng thái active. FE admin dùng trong flow moderation khi quyết định cho user truy cập hệ thống trở lại và phải lưu lại lý do mở khóa.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mở khóa người dùng thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy người dùng")
    })
    @PostMapping("/{userId}/unban")
    public ApiResponse<SystemUserResponse> unbanUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody UnbanUserRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(adminUserService.changeUserStatus(principal.getPublicId(), userId, false, request.getReason()));
    }
}
