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
@Tag(name = "Admin - Users", description = "Admin user listing, status configuration (lock/unlock), and role assignment")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "Lấy danh sách user thường dành cho admin",
            description = "Chỉ trả về user có role MENTEE hoặc MENTOR. Loại bỏ hoàn toàn ADMIN và SYSTEM_ADMIN. " +
                    "Quy tắc lọc theo role:\n" +
                    "- role=MENTEE: Trả về người dùng chỉ có vai trò MENTEE duy nhất (pure mentee), loại trừ người dùng có cả vai trò MENTOR.\n" +
                    "- role=MENTOR: Trả về tất cả người dùng có khả năng làm mentor (bao gồm cả những người dùng có cả 2 vai trò MENTEE và MENTOR).\n" +
                    "- Không truyền role: Trả về tất cả người dùng thuộc nhóm visible (không có quyền ADMIN/SYSTEM_ADMIN).\n\n" +
                    "Hỗ trợ phân trang (page, size), tìm kiếm theo từ khóa (keyword), lọc theo trạng thái hoạt động (status) và sắp xếp (sortBy, direction)."
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

    @Operation(summary = "Đình chỉ/Khóa tài khoản của người dùng (Banned)")
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

    @Operation(summary = "Mở khóa/Kích hoạt lại tài khoản của người dùng (Active)")
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
