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
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Nhóm API dùng cho đăng nhập Google, làm mới token, đăng xuất và lấy thông tin user hiện tại. FE dùng nhóm này ở đầu luồng onboarding và khi cần khôi phục phiên đăng nhập.")
public class AuthController {

    private final IdentityService identityService;
    private final InMemoryRateLimitService rateLimitService;

    @Operation(summary = "Đăng nhập bằng Google", description = "Xác thực user bằng Google ID token hoặc authorization-code flow và phát hành token riêng của SkillSwap để gọi các API phía sau. FE dùng đây là API đầu tiên trong luồng authentication trước khi gọi API lấy current user. Nếu là lần đăng nhập đầu, backend có thể tự tạo account mới; refresh token được trả qua HttpOnly cookie còn body giữ access token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công, trả về token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID Token Google không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản bị khóa hoặc chưa kích hoạt")
    })
    @PostMapping("/google")
    public ApiResponse<TokenResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        rateLimitService.check(
                "auth:google:" + resolveClientKey(httpServletRequest),
                20,
                java.time.Duration.ofMinutes(10),
                "Bạn đang đăng nhập quá nhanh, vui lòng thử lại sau ít phút"
        );
        TokenResponse tokenResponse = identityService.loginWithGoogle(request);
        addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        tokenResponse.setRefreshToken(null);
        return ApiResponse.success(tokenResponse);
    }

    @Operation(summary = "Làm mới access token", description = "Cấp access token mới từ refresh token còn hiệu lực. FE dùng khi access token hết hạn và cần gia hạn phiên đăng nhập mà không bắt user đăng nhập lại. Refresh token có thể được lấy từ request body hoặc từ HttpOnly cookie theo flow backend hiện tại. Khi cùng một refresh token cũ được dùng song song trong khoảng grace period, backend sẽ replay lại đúng cùng một token pair thay vì phát sinh token thứ ba.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Làm mới token thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token đã hết hạn hoặc bị thu hồi")
    })
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        rateLimitService.check(
                "auth:refresh:" + resolveClientKey(httpServletRequest),
                40,
                java.time.Duration.ofMinutes(10),
                "Bạn đang làm mới phiên đăng nhập quá nhanh, vui lòng thử lại sau"
        );
        String refreshToken = resolveRefreshToken(request, httpServletRequest);
        TokenResponse tokenResponse = identityService.refreshToken(refreshToken);
        addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        tokenResponse.setRefreshToken(null);
        return ApiResponse.success(tokenResponse);
    }

    @Operation(summary = "Đăng xuất phiên hiện tại", description = "Thu hồi refresh token hiện tại và kết thúc khả năng gia hạn phiên đăng nhập của user. FE dùng khi user logout khỏi thiết bị hoặc browser hiện tại. Access token đang có vẫn hết hạn theo lifetime tự nhiên của nó.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng xuất thành công")
    })
    @PostMapping("/logout")
    public ApiResponse<String> logout(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        String refreshToken = resolveRefreshToken(request, httpServletRequest);
        identityService.logout(refreshToken);
        clearRefreshTokenCookie(response);
        return ApiResponse.success("Đăng xuất thành công");
    }

    @Operation(summary = "Lấy thông tin user hiện tại", description = "Trả về thông tin user hiện tại dựa trên access token. FE dùng ngay sau khi sign-in hoặc refresh để biết roles, các cờ profile và quyết định nên tiếp tục onboarding hay cho user vào trải nghiệm chính của hệ thống.")
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

    private String resolveRefreshToken(RefreshTokenRequest request, HttpServletRequest httpServletRequest) {
        if (request != null && StringUtils.hasText(request.getRefreshToken())) {
            return request.getRefreshToken();
        }
        if (httpServletRequest != null && httpServletRequest.getCookies() != null) {
            String cookieName = identityService.getRefreshTokenCookieName();
            for (var cookie : httpServletRequest.getCookies()) {
                if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        throw new BaseException(ErrorCode.BAD_REQUEST, "Refresh token không được để trống");
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, identityService.buildRefreshTokenCookieValue(refreshToken));
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, identityService.buildRefreshTokenCookieValue("", true));
    }

    private String resolveClientKey(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex >= 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
        }
        return StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr() : "unknown";
    }
}
