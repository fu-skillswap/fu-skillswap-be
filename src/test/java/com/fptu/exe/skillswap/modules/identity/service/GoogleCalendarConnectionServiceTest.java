package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarConnectionRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarConnectionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleCalendarConnectionRepository connectionRepository;

    @Mock
    private GoogleCalendarApiClient googleCalendarApiClient;

    @Mock
    private GoogleTokenCryptoService googleTokenCryptoService;

    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private GoogleOAuthStateService googleOAuthStateService;

    private GoogleCalendarConnectionService service;

    @BeforeEach
    void setUp() {
        GoogleApiProperties googleApiProperties = new GoogleApiProperties();
        googleApiProperties.setCalendarRedirectUri("https://skillswap.asia/google-calendar/callback");
        org.springframework.transaction.support.TransactionTemplate transactionTemplate = org.mockito.Mockito.mock(org.springframework.transaction.support.TransactionTemplate.class);
        service = new GoogleCalendarConnectionService(
                userRepository,
                connectionRepository,
                googleCalendarApiClient,
                googleTokenCryptoService,
                transactionTemplate,
                googleAuthService,
                googleOAuthStateService,
                googleApiProperties
        );
    }

    @Test
    void resolveUserInfoForLogin_shouldRejectRedirectUriMismatchWhenConfigured() {
        GoogleLoginRequest request = new GoogleLoginRequest(
                "auth-code",
                "https://malicious.example.com/callback",
                "verifier",
                "state"
        );

        assertThrows(com.fptu.exe.skillswap.shared.exception.BaseException.class,
                () -> service.resolveUserInfoForLogin(request));
        verify(googleCalendarApiClient, never()).exchangeAuthorizationCode(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
