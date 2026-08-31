package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnection;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnectionStatus;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleCalendarConnectRequest;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarConnectionRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.port.MentorCalendarEligibilityPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityQueryPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarConnectionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private GoogleCalendarConnectionRepository connectionRepository;
    @Mock private GoogleCalendarApiClient googleCalendarApiClient;
    @Mock private GoogleTokenCryptoService googleTokenCryptoService;
    @Mock private GoogleOAuthStateService googleOAuthStateService;
    @Mock private MentorCalendarEligibilityPort mentorCalendarEligibilityPort;
    @Mock private BookingAvailabilityQueryPort bookingAvailabilityQueryPort;
    @Mock private TransactionTemplate transactionTemplate;

    private GoogleCalendarConnectionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        GoogleApiProperties properties = new GoogleApiProperties();
        properties.setCalendarRedirectUri("https://skillswap.asia/vi/mentor/google-calendar/callback");
        service = new GoogleCalendarConnectionService(
                userRepository,
                connectionRepository,
                googleCalendarApiClient,
                googleTokenCryptoService,
                transactionTemplate,
                googleOAuthStateService,
                properties,
                mentorCalendarEligibilityPort,
                bookingAvailabilityQueryPort
        );
        userId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void issueAuthorizationContext_shouldRejectRedirectMismatchBeforeIssuingState() {
        assertThrows(BaseException.class, () -> service.issueAuthorizationContext(
                userId,
                "https://malicious.example/callback",
                "challenge"
        ));

        verify(googleOAuthStateService, never()).issueCalendarConnect(any(), any(), any());
    }

    @Test
    void connect_shouldConsumeUserBoundCalendarStateBeforeExchangingCode() {
        User user = User.builder()
                .id(userId)
                .roles(new LinkedHashSet<>(java.util.List.of(RoleCode.MENTOR)))
                .build();
        GoogleCalendarConnectRequest request = new GoogleCalendarConnectRequest(
                "auth-code",
                "https://skillswap.asia/vi/mentor/google-calendar/callback",
                "verifier",
                "state"
        );
        when(googleCalendarApiClient.exchangeAuthorizationCode(any(), any(), any()))
                .thenReturn(new GoogleCalendarApiClient.GoogleTokenResponse(
                        "access", "refresh", 3600L,
                        "openid email https://www.googleapis.com/auth/calendar", null
                ));
        when(googleCalendarApiClient.fetchUserInfo("access"))
                .thenReturn(new GoogleCalendarApiClient.GoogleUserInfoResponse(
                        "google-sub", "mentor@fpt.edu.vn", "Mentor", null, true
                ));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(connectionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());
        when(googleTokenCryptoService.encrypt(any())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(googleTokenCryptoService.currentKeyVersion()).thenReturn(1);
        when(connectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.connect(userId, request);

        assertEquals(true, response.connected());
        verify(googleOAuthStateService).consumeCalendarConnect(
                userId, request.state(), request.redirectUri(), request.codeVerifier()
        );
        verify(googleCalendarApiClient).exchangeAuthorizationCode(
                request.authorizationCode(), request.redirectUri(), request.codeVerifier()
        );
    }

    @Test
    void disconnect_shouldBeBlockedWhileMentorHasActiveService() {
        GoogleCalendarConnection connection = GoogleCalendarConnection.builder()
                .user(User.builder().id(userId).build())
                .connectionStatus(GoogleCalendarConnectionStatus.ACTIVE)
                .build();
        when(connectionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(connection));
        when(mentorCalendarEligibilityPort.hasActiveOneToOneService(userId)).thenReturn(true);

        BaseException exception = assertThrows(BaseException.class, () -> service.disconnect(userId));

        assertEquals(ErrorCode.GOOGLE_CALENDAR_DISCONNECT_BLOCKED, exception.getErrorCode());
        verify(connectionRepository, never()).save(any());
        verify(googleCalendarApiClient, never()).revokeToken(any());
    }

    @Test
    void requireActiveConnectionForServiceCreation_shouldRejectReconnectState() {
        GoogleCalendarConnection connection = GoogleCalendarConnection.builder()
                .connectionStatus(GoogleCalendarConnectionStatus.REQUIRES_RECONNECT)
                .build();
        when(connectionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(connection));

        BaseException exception = assertThrows(
                BaseException.class,
                () -> service.requireActiveConnectionForServiceCreation(userId)
        );

        assertEquals(ErrorCode.GOOGLE_CALENDAR_CONNECTION_REQUIRED, exception.getErrorCode());
    }
}
