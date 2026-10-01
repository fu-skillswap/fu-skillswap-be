package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Account Management", description = "Quản lý tài khoản cá nhân, bao gồm yêu cầu xóa tài khoản theo chính sách bảo mật của Google Play Store.")
public class AccountController {

    private final IdentityService identityService;

    @Operation(
            summary = "Xóa tài khoản người dùng hiện tại",
            description = "Thực hiện xóa tài khoản, ẩn thông tin định danh cá nhân (PII), hủy toàn bộ phiên làm việc và giải phóng tài nguyên theo chính sách của Google Play Store."
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping
    public ApiResponse<String> deleteMyAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse response
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        identityService.deleteMyAccount(principal.getPublicId());
        if (response != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, identityService.buildRefreshTokenCookieValue("", true));
        }
        return ApiResponse.success("Tài khoản của bạn đã được xóa thành công");
    }
}
