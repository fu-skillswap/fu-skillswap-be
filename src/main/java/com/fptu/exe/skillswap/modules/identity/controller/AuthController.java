package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.RefreshTokenRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserMeResponse;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Xác thực", description = "Các API đăng nhập, làm mới token và đăng xuất")
public class AuthController {

    private final IdentityService identityService;

    @Operation(summary = "Đăng nhập bằng Google", description = "Xác thực người dùng thông qua Google ID Token. " +
            "Nếu tài khoản chưa tồn tại, hệ thống sẽ tự động tạo mới với vai trò MENTEE. " +
            "Nếu email nằm trong cấu hình SYSTEM_ADMIN_EMAILS, access token trả về sẽ có thêm vai trò SYSTEM_ADMIN. " +
            "Trả về cặp access token và refresh token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công, trả về token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID Token Google không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản bị khóa hoặc chưa kích hoạt")
    })
    @PostMapping("/google")
    public ApiResponse<TokenResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        TokenResponse tokenResponse = identityService.loginWithGoogle(request);
        return ApiResponse.success(tokenResponse);
    }

    @Operation(summary = "Làm mới Access Token", description = "Sử dụng Refresh Token còn hiệu lực để cấp lại Access Token mới. "
            +
            "Refresh Token cũ sẽ bị thu hồi ngay sau khi token mới được phát hành (Refresh Token Rotation).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Làm mới token thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token đã hết hạn hoặc bị thu hồi")
    })
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = identityService.refreshToken(request);
        return ApiResponse.success(tokenResponse);
    }

    @Operation(summary = "Đăng xuất", description = "Thu hồi Refresh Token hiện tại, kết thúc phiên đăng nhập của người dùng. "
            +
            "Access Token sẽ vẫn còn hiệu lực cho đến khi hết hạn tự nhiên.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng xuất thành công")
    })
    @PostMapping("/logout")
    public ApiResponse<String> logout(@Valid @RequestBody RefreshTokenRequest request) {
        identityService.logout(request.getRefreshToken());
        return ApiResponse.success("Đăng xuất thành công");
    }

    @Operation(summary = "Lấy thông tin người dùng hiện tại", description = "Trả về thông tin của người dùng đang đăng nhập dựa trên JWT token, "
            +
            "bao gồm trạng thái hoàn thành hồ sơ học thuật (`profileCompleted`, `hasStudentProfile`). " +
            "FE dùng response này để quyết định chuyển hướng vào dashboard hay trang điền hồ sơ.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        UserMeResponse userMe = identityService.getCurrentUser(principal.getPublicId());
        return ApiResponse.success(userMe);
    }
}
