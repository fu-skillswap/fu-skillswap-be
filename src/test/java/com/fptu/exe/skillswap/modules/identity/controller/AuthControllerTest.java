package com.fptu.exe.skillswap.modules.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.TrustedClientIpResolver;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailRegisterRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailResendOtpRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailVerifyRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleMobileLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.EmailRegisterResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.service.EmailAuthService;
import com.fptu.exe.skillswap.modules.identity.service.GoogleLoginNonceService;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String DUMMY_ACCESS = "test_access_token";
    private static final String DUMMY_REFRESH = "test_refresh_token";
    private static final String DUMMY_COOKIE_REFRESH = "test_cookie_refresh_token";

    @Mock
    private IdentityService identityService;

    @Mock
    private EmailAuthService emailAuthService;

    @Mock
    private GoogleLoginNonceService googleLoginNonceService;

    @Mock
    private InMemoryRateLimitService rateLimitService;

    @Mock
    private TrustedClientIpResolver trustedClientIpResolver;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        lenient().when(trustedClientIpResolver.resolve(any())).thenReturn("127.0.0.1");
        lenient().when(identityService.getRefreshTokenCookieName()).thenReturn("refresh_token");
        lenient().when(identityService.buildRefreshTokenCookieValue(anyString())).thenAnswer(inv -> "refresh_token=" + inv.getArgument(0) + "; Path=/api/auth; HttpOnly");
        lenient().when(identityService.buildRefreshTokenCookieValue(anyString(), anyBoolean())).thenReturn("refresh_token=; Max-Age=0; Path=/api/auth; HttpOnly");
    }

    @Test
    @DisplayName("Mobile Login returns refreshToken in JSON body and sets cookie")
    void loginWithGoogleMobile_returnsRefreshTokenInBodyAndCookie() throws Exception {
        GoogleMobileLoginRequest request = new GoogleMobileLoginRequest("dummy_mobile_credential");
        TokenResponse serviceTokenResponse = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(identityService.loginWithGoogleMobile("dummy_mobile_credential")).thenReturn(serviceTokenResponse);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.loginWithGoogleMobile(request, servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertEquals(DUMMY_REFRESH, response.getData().getRefreshToken());
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains(DUMMY_REFRESH));

        // Verify JSON serialization includes refreshToken for mobile
        String json = objectMapper.writeValueAsString(response.getData());
        assertTrue(json.contains("\"refreshToken\":\"" + DUMMY_REFRESH + "\""));
    }

    @Test
    @DisplayName("Web Login sets refreshToken in cookie and clears it from JSON body")
    void loginWithGoogle_web_excludesRefreshTokenFromBody() throws Exception {
        GoogleLoginRequest request = new GoogleLoginRequest("dummy_web_cred", "dummy_nonce");
        TokenResponse serviceTokenResponse = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(identityService.loginWithGoogle(request)).thenReturn(serviceTokenResponse);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.loginWithGoogle(request, servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertNull(response.getData().getRefreshToken());
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains(DUMMY_REFRESH));

        // Verify JSON serialization does NOT include refreshToken
        String json = objectMapper.writeValueAsString(response.getData());
        assertFalse(json.contains("\"refreshToken\""));
    }

    @Test
    @DisplayName("Mobile Refresh with token in body returns new refreshToken in JSON body")
    void refreshToken_mobileWithBody_returnsRefreshTokenInBody() throws Exception {
        TokenResponse newPair = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(identityService.refreshToken(DUMMY_REFRESH)).thenReturn(newPair);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setContentType("application/json");
        servletRequest.setContent(("{\"refreshToken\":\"" + DUMMY_REFRESH + "\"}").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.refreshToken(servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertEquals(DUMMY_REFRESH, response.getData().getRefreshToken());

        String json = objectMapper.writeValueAsString(response.getData());
        assertTrue(json.contains("\"refreshToken\":\"" + DUMMY_REFRESH + "\""));
    }

    @Test
    @DisplayName("Mobile Refresh with X-Client-Type: mobile header returns new refreshToken in JSON body")
    void refreshToken_mobileWithHeader_returnsRefreshTokenInBody() throws Exception {
        TokenResponse newPair = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(identityService.refreshToken(DUMMY_COOKIE_REFRESH)).thenReturn(newPair);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("refresh_token", DUMMY_COOKIE_REFRESH));
        servletRequest.addHeader("X-Client-Type", "mobile");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.refreshToken(servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals(DUMMY_REFRESH, response.getData().getRefreshToken());
    }

    @Test
    @DisplayName("Web Refresh with cookie only returns refreshToken = null in body and sets cookie")
    void refreshToken_webWithCookie_excludesRefreshTokenFromBody() throws Exception {
        TokenResponse newPair = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(identityService.refreshToken(DUMMY_COOKIE_REFRESH)).thenReturn(newPair);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("refresh_token", DUMMY_COOKIE_REFRESH));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.refreshToken(servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertNull(response.getData().getRefreshToken());
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains(DUMMY_REFRESH));

        String json = objectMapper.writeValueAsString(response.getData());
        assertFalse(json.contains("\"refreshToken\""));
    }

    @Test
    @DisplayName("Refresh without body and without cookie throws BAD_REQUEST")
    void refreshToken_missingToken_throwsBadRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        BaseException ex = assertThrows(BaseException.class, () ->
                authController.refreshToken(servletRequest, servletResponse)
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Mobile Logout with token in body revokes token via IdentityService")
    void logout_mobileWithBody_revokesToken() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setContentType("application/json");
        servletRequest.setContent(("{\"refreshToken\":\"" + DUMMY_REFRESH + "\"}").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<String> response = authController.logout(servletRequest, servletResponse);

        assertEquals("Đăng xuất thành công", response.getData());
        verify(identityService).logout(DUMMY_REFRESH);
    }

    @Test
    @DisplayName("Web Logout with cookie revokes token and clears cookie")
    void logout_webWithCookie_revokesTokenAndClearsCookie() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("refresh_token", DUMMY_COOKIE_REFRESH));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<String> response = authController.logout(servletRequest, servletResponse);

        assertEquals("Đăng xuất thành công", response.getData());
        verify(identityService).logout(DUMMY_COOKIE_REFRESH);
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    @Test
    @DisplayName("Logout without token throws BAD_REQUEST")
    void logout_missingToken_throwsBadRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        BaseException ex = assertThrows(BaseException.class, () ->
                authController.logout(servletRequest, servletResponse)
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Register with email sends OTP and returns register response")
    void register_success() {
        EmailRegisterRequest request = new EmailRegisterRequest("test@fpt.edu.vn", "Password@123", "User Test");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        EmailRegisterResponse mockResponse = new EmailRegisterResponse("test@fpt.edu.vn", false, "Mã xác thực đã gửi");
        when(emailAuthService.register(request)).thenReturn(mockResponse);

        ApiResponse<EmailRegisterResponse> response = authController.register(request, servletRequest);

        assertNotNull(response);
        assertEquals("test@fpt.edu.vn", response.getData().email());
        assertFalse(response.getData().isLinked());
        verify(rateLimitService).check(eq(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY),
                eq("auth:register:127.0.0.1"), eq(10), any(), any());
        verify(emailAuthService).register(request);
    }

    @Test
    @DisplayName("Resend OTP triggers emailAuthService with email-scoped rate limit")
    void resendOtp_success() {
        EmailResendOtpRequest request = new EmailResendOtpRequest("test@fpt.edu.vn");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        ApiResponse<String> response = authController.resendOtp(request, servletRequest);

        assertNotNull(response);
        assertTrue(response.getData().contains("Mã xác thực mới"));
        verify(rateLimitService).check(eq(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY),
                eq("auth:resend:test@fpt.edu.vn"), eq(1), any(), any());
        verify(emailAuthService).resendOtp(request);
    }

    @Test
    @DisplayName("Verify email for web client sets cookie and clears refresh token in body")
    void verifyEmail_webClient_setsCookieAndClearsRefreshTokenFromBody() {
        EmailVerifyRequest request = new EmailVerifyRequest("test@fpt.edu.vn", "123456");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(emailAuthService.verifyEmail(request)).thenReturn(tokenResponse);

        ApiResponse<TokenResponse> response = authController.verifyEmail(request, servletRequest, servletResponse);

        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertNull(response.getData().getRefreshToken());
        assertEquals("refresh_token=" + DUMMY_REFRESH + "; Path=/api/auth; HttpOnly", servletResponse.getHeader(HttpHeaders.SET_COOKIE));
        verify(emailAuthService).verifyEmail(request);
    }

    @Test
    @DisplayName("Verify email for mobile client preserves refresh token in body")
    void verifyEmail_mobileClient_preservesRefreshTokenInBody() {
        EmailVerifyRequest request = new EmailVerifyRequest("test@fpt.edu.vn", "123456");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Client-Type", "mobile");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(emailAuthService.verifyEmail(request)).thenReturn(tokenResponse);

        ApiResponse<TokenResponse> response = authController.verifyEmail(request, servletRequest, servletResponse);

        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertEquals(DUMMY_REFRESH, response.getData().getRefreshToken());
        assertEquals("refresh_token=" + DUMMY_REFRESH + "; Path=/api/auth; HttpOnly", servletResponse.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("Login for web client sets cookie and clears refresh token in body")
    void login_webClient_setsCookieAndClearsRefreshTokenFromBody() {
        EmailLoginRequest request = new EmailLoginRequest("test@fpt.edu.vn", "Password@123");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(emailAuthService.login(request)).thenReturn(tokenResponse);

        ApiResponse<TokenResponse> response = authController.login(request, servletRequest, servletResponse);

        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertNull(response.getData().getRefreshToken());
        assertEquals("refresh_token=" + DUMMY_REFRESH + "; Path=/api/auth; HttpOnly", servletResponse.getHeader(HttpHeaders.SET_COOKIE));
        verify(rateLimitService).check(eq(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.SECURITY),
                eq("auth:login:127.0.0.1"), eq(10), any(), any());
    }

    @Test
    @DisplayName("Login for mobile client preserves refresh token in body")
    void login_mobileClient_preservesRefreshTokenInBody() {
        EmailLoginRequest request = new EmailLoginRequest("test@fpt.edu.vn", "Password@123");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Client-Type", "mobile");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(DUMMY_ACCESS)
                .refreshToken(DUMMY_REFRESH)
                .tokenType("Bearer")
                .build();
        when(emailAuthService.login(request)).thenReturn(tokenResponse);

        ApiResponse<TokenResponse> response = authController.login(request, servletRequest, servletResponse);

        assertEquals(DUMMY_ACCESS, response.getData().getAccessToken());
        assertEquals(DUMMY_REFRESH, response.getData().getRefreshToken());
    }
}
