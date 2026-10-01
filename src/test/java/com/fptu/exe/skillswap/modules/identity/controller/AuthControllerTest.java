package com.fptu.exe.skillswap.modules.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.TrustedClientIpResolver;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleMobileLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.LogoutRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.RefreshTokenRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private IdentityService identityService;

    @Mock
    private GoogleLoginNonceService googleLoginNonceService;

    @Mock
    private InMemoryRateLimitService rateLimitService;

    @Mock
    private TrustedClientIpResolver trustedClientIpResolver;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        GoogleMobileLoginRequest request = new GoogleMobileLoginRequest("mobile-id-token");
        TokenResponse serviceTokenResponse = TokenResponse.builder()
                .accessToken("acc-123")
                .refreshToken("ref-456")
                .tokenType("Bearer")
                .build();
        when(identityService.loginWithGoogleMobile("mobile-id-token")).thenReturn(serviceTokenResponse);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.loginWithGoogleMobile(request, servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals("acc-123", response.getData().getAccessToken());
        assertEquals("ref-456", response.getData().getRefreshToken());
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains("ref-456"));

        // Verify JSON serialization includes refreshToken for mobile
        String json = objectMapper.writeValueAsString(response.getData());
        assertTrue(json.contains("\"refreshToken\":\"ref-456\""));
    }

    @Test
    @DisplayName("Web Login sets refreshToken in cookie and clears it from JSON body")
    void loginWithGoogle_web_excludesRefreshTokenFromBody() throws Exception {
        GoogleLoginRequest request = new GoogleLoginRequest("web-cred", "nonce-1");
        TokenResponse serviceTokenResponse = TokenResponse.builder()
                .accessToken("acc-web")
                .refreshToken("ref-web")
                .tokenType("Bearer")
                .build();
        when(identityService.loginWithGoogle(request)).thenReturn(serviceTokenResponse);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.loginWithGoogle(request, servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals("acc-web", response.getData().getAccessToken());
        assertNull(response.getData().getRefreshToken());
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains("ref-web"));

        // Verify JSON serialization does NOT include refreshToken
        String json = objectMapper.writeValueAsString(response.getData());
        assertFalse(json.contains("\"refreshToken\""));
    }

    @Test
    @DisplayName("Mobile Refresh with token in body returns new refreshToken in JSON body")
    void refreshToken_mobileWithBody_returnsRefreshTokenInBody() throws Exception {
        RefreshTokenRequest body = new RefreshTokenRequest("ref-mobile-current");
        TokenResponse newPair = TokenResponse.builder()
                .accessToken("acc-new")
                .refreshToken("ref-mobile-new")
                .tokenType("Bearer")
                .build();
        when(identityService.refreshToken("ref-mobile-current")).thenReturn(newPair);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.refreshToken(body, servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals("acc-new", response.getData().getAccessToken());
        assertEquals("ref-mobile-new", response.getData().getRefreshToken());

        String json = objectMapper.writeValueAsString(response.getData());
        assertTrue(json.contains("\"refreshToken\":\"ref-mobile-new\""));
    }

    @Test
    @DisplayName("Mobile Refresh with X-Client-Type: mobile header returns new refreshToken in JSON body")
    void refreshToken_mobileWithHeader_returnsRefreshTokenInBody() throws Exception {
        TokenResponse newPair = TokenResponse.builder()
                .accessToken("acc-new-2")
                .refreshToken("ref-mobile-new-2")
                .tokenType("Bearer")
                .build();
        when(identityService.refreshToken("ref-from-cookie")).thenReturn(newPair);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("refresh_token", "ref-from-cookie"));
        servletRequest.addHeader("X-Client-Type", "mobile");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.refreshToken(null, servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals("ref-mobile-new-2", response.getData().getRefreshToken());
    }

    @Test
    @DisplayName("Web Refresh with cookie only returns refreshToken = null in body and sets cookie")
    void refreshToken_webWithCookie_excludesRefreshTokenFromBody() throws Exception {
        TokenResponse newPair = TokenResponse.builder()
                .accessToken("acc-web-new")
                .refreshToken("ref-web-new")
                .tokenType("Bearer")
                .build();
        when(identityService.refreshToken("cookie-token")).thenReturn(newPair);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("refresh_token", "cookie-token"));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<TokenResponse> response = authController.refreshToken(null, servletRequest, servletResponse);

        assertNotNull(response.getData());
        assertEquals("acc-web-new", response.getData().getAccessToken());
        assertNull(response.getData().getRefreshToken());
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains("ref-web-new"));

        String json = objectMapper.writeValueAsString(response.getData());
        assertFalse(json.contains("\"refreshToken\""));
    }

    @Test
    @DisplayName("Refresh without body and without cookie throws BAD_REQUEST")
    void refreshToken_missingToken_throwsBadRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        BaseException ex = assertThrows(BaseException.class, () ->
                authController.refreshToken(null, servletRequest, servletResponse)
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Mobile Logout with token in body revokes token via IdentityService")
    void logout_mobileWithBody_revokesToken() {
        LogoutRequest body = new LogoutRequest("mobile-logout-token");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<String> response = authController.logout(body, servletRequest, servletResponse);

        assertEquals("Đăng xuất thành công", response.getData());
        verify(identityService).logout("mobile-logout-token");
    }

    @Test
    @DisplayName("Web Logout with cookie revokes token and clears cookie")
    void logout_webWithCookie_revokesTokenAndClearsCookie() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("refresh_token", "cookie-logout-token"));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<String> response = authController.logout(null, servletRequest, servletResponse);

        assertEquals("Đăng xuất thành công", response.getData());
        verify(identityService).logout("cookie-logout-token");
        assertTrue(servletResponse.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    @Test
    @DisplayName("Logout without token throws BAD_REQUEST")
    void logout_missingToken_throwsBadRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        BaseException ex = assertThrows(BaseException.class, () ->
                authController.logout(null, servletRequest, servletResponse)
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
