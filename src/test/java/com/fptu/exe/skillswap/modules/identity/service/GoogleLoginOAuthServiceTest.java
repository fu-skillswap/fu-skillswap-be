package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleLoginOAuthServiceTest {

    @Mock private GoogleCalendarApiClient googleApiClient;
    @Mock private GoogleAuthService googleAuthService;
    @Mock private GoogleOAuthStateService stateService;

    private GoogleLoginOAuthService service;

    @BeforeEach
    void setUp() {
        GoogleApiProperties properties = new GoogleApiProperties();
        properties.setLoginRedirectUris(
                "http://localhost:3000/vi/auth/google/callback,https://skillswap.asia/vi/auth/google/callback"
        );
        service = new GoogleLoginOAuthService(googleApiClient, googleAuthService, stateService, properties);
    }

    @Test
    void issueAuthorizationContext_shouldUseOnlyLoginRedirectWhitelist() {
        assertThrows(BaseException.class, () -> service.issueAuthorizationContext(
                "https://skillswap.asia/vi/mentor/google-calendar/callback",
                "challenge"
        ));

        verify(stateService, never()).issueLogin(any(), any());
    }

    @Test
    void resolveUserInfo_shouldConsumeLoginStateAndNotCalendarState() {
        GoogleLoginRequest request = new GoogleLoginRequest(
                "code",
                "https://skillswap.asia/vi/auth/google/callback",
                "verifier",
                "state"
        );
        GoogleAuthService.GoogleUserInfo expected = new GoogleAuthService.GoogleUserInfo();
        expected.setSub("sub");
        expected.setEmail("user@fpt.edu.vn");
        expected.setName("User");
        expected.setEmail_verified("true");
        when(googleApiClient.exchangeAuthorizationCode("code", request.getRedirectUri(), "verifier"))
                .thenReturn(new GoogleCalendarApiClient.GoogleTokenResponse(
                        "access", null, 3600L, "openid email profile", "id-token"
                ));
        when(googleAuthService.verifyToken("id-token")).thenReturn(expected);

        var result = service.resolveUserInfo(request);

        assertEquals(expected, result);
        verify(stateService).consumeLogin("state", request.getRedirectUri(), "verifier");
        verify(stateService, never()).consumeCalendarConnect(any(), any(), any(), any());
    }
}
