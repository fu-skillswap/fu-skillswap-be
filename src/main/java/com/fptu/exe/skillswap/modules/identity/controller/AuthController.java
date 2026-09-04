package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleLoginNonceResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserMeResponse;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import com.fptu.exe.skillswap.modules.identity.service.GoogleLoginNonceService;
import com.fptu.exe.skillswap.infrastructure.security.TrustedClientIpResolver;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
    private final GoogleLoginNonceService googleLoginNonceService;
    private final InMemoryRateLimitService rateLimitService;
    private final TrustedClientIpResolver trustedClientIpResolver;

    @Operation(summary = "Cấp nonce đăng nhập Google", description = "Phát hành nonce dùng một lần cho Google Identity Services. FE phải truyền nonce này vào GIS trước khi nhận credential.")
    @GetMapping("/google/nonce")
    public ApiResponse<GoogleLoginNonceResponse> createGoogleLoginNonce(HttpServletRequest request) {
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY,
                "auth:google-nonce:" + resolveClientKey(request),
                60,
                java.time.Duration.ofMinutes(10),
                "Bạn đang khởi tạo đăng nhập quá nhanh, vui lòng thử lại sau"
        );
        return ApiResponse.success(googleLoginNonceService.issue());
    }

    @Operation(summary = "Đăng nhập bằng Google GIS", description = "Xác minh Google ID Token, audience, issuer, email_verified và nonce dùng một lần. Refresh token SkillSwap chỉ được trả qua HttpOnly cookie.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Đăng nhập thành công; access token ở body, refresh token được rotate trong HttpOnly cookie.",
                    content = @Content(examples = @ExampleObject(
                            name = "Đăng nhập thành công",
                            value = """
                                    {
                                      "timestamp": "2026-09-04T03:15:30Z",
                                      "status": 200,
                                      "code": "SUCCESS_0200",
                                      "message": "Thành công",
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.demo-access-token",
                                        "tokenType": "Bearer"
                                      }
                                    }
                                    """)),
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Browser-managed refresh cookie. HttpOnly; Secure và SameSite phụ thuộc environment/config; Path=/api/auth. Swagger UI không thể dán cookie này thủ công.",
                            schema = @Schema(type = "string", example = "refresh_token=<redacted>; Path=/api/auth; HttpOnly; SameSite=Lax")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Google credential hoặc nonce không hợp lệ, đã dùng hoặc hết hạn", content = @Content(examples = @ExampleObject(
                    name = "Credential hoặc nonce không hợp lệ",
                    value = """
                            {
                              "status": 400,
                              "code": "AUTH_1006",
                              "message": "Xác thực Google thất bại"
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản bị khóa hoặc chưa kích hoạt", content = @Content(examples = @ExampleObject(
                    name = "Tài khoản không được phép đăng nhập",
                    value = """
                            {
                              "status": 403,
                              "code": "AUTH_1004",
                              "message": "Tài khoản đã bị khóa"
                            }
                            """)))
    })
    @PostMapping("/google")
    public ApiResponse<TokenResponse> loginWithGoogle(
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FE gửi credential do Google Identity Services trả về và nonce đã lấy từ GET /api/auth/google/nonce.",
                    content = @Content(examples = @ExampleObject(
                            name = "Request đăng nhập Google",
                            value = """
                                    {
                                      "credential": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImRlbW8uLi4",
                                      "nonce": "pBt_T5mF-demo-nonce"
                                    }
                                    """)))
            GoogleLoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY,
                "auth:google:" + resolveClientKey(httpServletRequest),
                60,
                java.time.Duration.ofMinutes(10),
                "Bạn đang đăng nhập quá nhanh, vui lòng thử lại sau ít phút"
        );
        TokenResponse tokenResponse = identityService.loginWithGoogle(request);
        addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        tokenResponse.setRefreshToken(null);
        return ApiResponse.success(tokenResponse);
    }

    @Operation(summary = "Làm mới access token", description = "Cấp access token mới từ refresh token trong HttpOnly cookie. Endpoint không nhận refresh token trong body. Các request song song trong grace period nhận cùng một token pair.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Làm mới access token thành công và rotate refresh cookie. Endpoint đọc cookie, không nhận refresh token trong body hoặc Bearer input.",
                    content = @Content(examples = @ExampleObject(
                            name = "Refresh thành công",
                            value = """
                                    {
                                      "timestamp": "2026-09-04T03:20:00Z",
                                      "status": 200,
                                      "code": "SUCCESS_0200",
                                      "message": "Thành công",
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.demo-refreshed-token",
                                        "tokenType": "Bearer"
                                      }
                                    }
                                    """)),
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Browser-managed refresh cookie được rotate. HttpOnly; Secure và SameSite phụ thuộc environment/config; Path=/api/auth.",
                            schema = @Schema(type = "string", example = "refresh_token=<redacted>; Path=/api/auth; HttpOnly; SameSite=Lax")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token đã hết hạn hoặc bị thu hồi. FE xóa session cục bộ và yêu cầu đăng nhập lại.", content = @Content(examples = @ExampleObject(
                    name = "Refresh token hết hạn",
                    value = """
                            {
                              "status": 401,
                              "code": "AUTH_1001",
                              "message": "Chưa xác thực người dùng"
                            }
                            """)))
    })
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY,
                "auth:refresh:" + resolveClientKey(httpServletRequest),
                40,
                java.time.Duration.ofMinutes(10),
                "Bạn đang làm mới phiên đăng nhập quá nhanh, vui lòng thử lại sau"
        );
        String refreshToken = resolveRefreshToken(httpServletRequest);
        TokenResponse tokenResponse = identityService.refreshToken(refreshToken);
        addRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        tokenResponse.setRefreshToken(null);
        return ApiResponse.success(tokenResponse);
    }

    @Operation(summary = "Đăng xuất phiên hiện tại", description = "Thu hồi refresh token hiện tại và kết thúc khả năng gia hạn phiên đăng nhập của user. FE dùng khi user logout khỏi thiết bị hoặc browser hiện tại. Access token đang có vẫn hết hạn theo lifetime tự nhiên của nó.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Đăng xuất thành công và trả Set-Cookie để browser xóa refresh cookie.",
                    headers = @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Refresh cookie bị xóa tại Path=/api/auth.",
                            schema = @Schema(type = "string", example = "refresh_token=; Max-Age=0; Path=/api/auth; HttpOnly; SameSite=Lax")
                    )
            )
    })
    @PostMapping("/logout")
    public ApiResponse<String> logout(
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        String refreshToken = resolveRefreshToken(httpServletRequest);
        identityService.logout(refreshToken);
        clearRefreshTokenCookie(response);
        return ApiResponse.success("Đăng xuất thành công");
    }

    @Operation(summary = "Lấy thông tin user hiện tại", description = "Trả về thông tin user hiện tại dựa trên access token. FE dùng ngay sau khi sign-in hoặc refresh để biết roles, các cờ profile và quyết định nên tiếp tục onboarding hay cho user vào trải nghiệm chính của hệ thống.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy thông tin thành công", content = @Content(examples = @ExampleObject(
                    name = "User hiện tại đã hoàn tất profile",
                    value = """
                            {
                              "status": 200,
                              "code": "SUCCESS_0200",
                              "message": "Thành công",
                              "data": {
                                "publicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                "email": "nguyenvana@gmail.com",
                                "fullName": "Nguyễn Văn A",
                                "avatarUrl": "https://lh3.googleusercontent.com/demo-avatar",
                                "status": "ACTIVE",
                                "roles": ["MENTEE"],
                                "profileCompleted": true,
                                "hasStudentProfile": true,
                                "googleCalendarConnected": false,
                                "googleCalendarSyncEnabled": false,
                                "googleCalendarNeedsReconnect": false
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn", content = @Content(examples = @ExampleObject(
                    name = "Session hết hạn",
                    value = """
                            {
                              "status": 401,
                              "code": "AUTH_1001",
                              "message": "Chưa xác thực người dùng"
                            }
                            """)))
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

    private String resolveRefreshToken(HttpServletRequest httpServletRequest) {
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
        return trustedClientIpResolver.resolve(request);
    }
}
