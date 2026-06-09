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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IdentityService identityService;

    @PostMapping("/google")
    public ApiResponse<TokenResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        TokenResponse tokenResponse = identityService.loginWithGoogle(request);
        return ApiResponse.success(tokenResponse);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse tokenResponse = identityService.refreshToken(request);
        return ApiResponse.success(tokenResponse);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@Valid @RequestBody RefreshTokenRequest request) {
        identityService.logout(request.getRefreshToken());
        return ApiResponse.success("Đăng xuất thành công");
    }

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        UserMeResponse userMe = identityService.getCurrentUser(principal.getPublicId());
        return ApiResponse.success(userMe);
    }
}
