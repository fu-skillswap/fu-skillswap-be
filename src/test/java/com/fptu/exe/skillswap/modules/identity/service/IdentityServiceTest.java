package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.config.RefreshTokenCookieProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserSessionState;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private GoogleCalendarConnectionService googleCalendarConnectionService;

    @Mock
    private IdentityLoginTransactionService identityLoginTransactionService;

    @Mock
    private RefreshTokenReplayCryptoService refreshTokenReplayCryptoService;

    @Mock
    private AcademicService academicService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenCookieProperties refreshTokenCookieProperties;

    private JwtProperties jwtProperties;

    private IdentityService identityService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.getJwt().setSecretKey("identity-service-test-secret");
        jwtProperties.getJwt().setExpiration(900_000L);
        jwtProperties.getJwt().getRefreshToken().setExpiration(1_800_000L);
        jwtProperties.getJwt().getRefreshToken().setRotationGracePeriodMillis(30_000L);

        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setFullName("Test User");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(new java.util.LinkedHashSet<>(List.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE)));

        identityService = new IdentityService(
                userRepository,
                userSessionRepository,
                googleAuthService,
                googleCalendarConnectionService,
                identityLoginTransactionService,
                refreshTokenReplayCryptoService,
                academicService,
                jwtTokenProvider,
                jwtProperties,
                refreshTokenCookieProperties
        );
    }

    @Test
    void refreshToken_shouldReplaySamePairDuringGracePeriod() {
        UserSession rotatingSession = new UserSession();
        rotatingSession.setId(UUID.randomUUID());
        rotatingSession.setUser(user);
        rotatingSession.setRefreshTokenHash("hash-1");
        rotatingSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        rotatingSession.setRevoked(false);
        rotatingSession.setSessionState(UserSessionState.ACTIVE);

        when(jwtTokenProvider.hashToken("refresh-token-1")).thenReturn("hash-1");
        when(userSessionRepository.findByRefreshTokenHashForUpdate("hash-1")).thenReturn(Optional.of(rotatingSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(eq(userId), eq(user.getEmail()), anyList())).thenReturn("access-token-2");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token-2");
        when(jwtTokenProvider.hashToken("refresh-token-2")).thenReturn("hash-2");
        when(refreshTokenReplayCryptoService.encrypt(any(TokenResponse.class))).thenReturn("cipher-1");
        when(refreshTokenReplayCryptoService.decrypt("cipher-1")).thenReturn(TokenResponse.builder()
                .accessToken("access-token-2")
                .refreshToken("refresh-token-2")
                .tokenType("Bearer")
                .build());
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });

        TokenResponse firstResponse = identityService.refreshToken("refresh-token-1");

        assertEquals("access-token-2", firstResponse.getAccessToken());
        assertEquals("refresh-token-2", firstResponse.getRefreshToken());
        assertEquals(UserSessionState.ROTATING_GRACE, rotatingSession.getSessionState());
        assertNotNull(rotatingSession.getGraceReplacementSessionId());

        TokenResponse secondResponse = identityService.refreshToken("refresh-token-1");

        assertEquals(firstResponse, secondResponse);
        verify(jwtTokenProvider, times(1)).generateAccessToken(eq(userId), eq(user.getEmail()), anyList());
        verify(jwtTokenProvider, times(1)).generateRefreshToken();
        verify(refreshTokenReplayCryptoService, times(1)).encrypt(any(TokenResponse.class));
        verify(refreshTokenReplayCryptoService, times(1)).decrypt("cipher-1");
    }

    @Test
    void refreshToken_shouldExpireGraceSessionAfterGraceWindow() {
        UserSession rotatingSession = new UserSession();
        rotatingSession.setId(UUID.randomUUID());
        rotatingSession.setUser(user);
        rotatingSession.setRefreshTokenHash("hash-1");
        rotatingSession.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        rotatingSession.setRevoked(false);
        rotatingSession.setSessionState(UserSessionState.ROTATING_GRACE);
        rotatingSession.setGraceExpiresAt(LocalDateTime.now().minusSeconds(1));
        rotatingSession.setGraceReplayCiphertext("cipher-1");

        when(jwtTokenProvider.hashToken("refresh-token-1")).thenReturn("hash-1");
        when(userSessionRepository.findByRefreshTokenHashForUpdate("hash-1")).thenReturn(Optional.of(rotatingSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var ex = assertThrows(com.fptu.exe.skillswap.shared.exception.BaseException.class,
                () -> identityService.refreshToken("refresh-token-1"));

        assertEquals(com.fptu.exe.skillswap.shared.exception.ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
        verify(refreshTokenReplayCryptoService, never()).decrypt(anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(any(UUID.class), anyString(), anyList());
    }

    @Test
    void logout_shouldRevokeReplacementSessionWhenInGrace() {
        UserSession rotatingSession = new UserSession();
        rotatingSession.setId(UUID.randomUUID());
        rotatingSession.setUser(user);
        rotatingSession.setRefreshTokenHash("hash-1");
        rotatingSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        rotatingSession.setRevoked(false);
        rotatingSession.setSessionState(UserSessionState.ROTATING_GRACE);
        UUID replacementId = UUID.randomUUID();
        rotatingSession.setGraceReplacementSessionId(replacementId);

        UserSession replacementSession = new UserSession();
        replacementSession.setId(replacementId);
        replacementSession.setUser(user);
        replacementSession.setRefreshTokenHash("hash-2");
        replacementSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        replacementSession.setRevoked(false);
        replacementSession.setSessionState(UserSessionState.ACTIVE);

        when(jwtTokenProvider.hashToken("refresh-token-1")).thenReturn("hash-1");
        when(userSessionRepository.findByRefreshTokenHashForUpdate("hash-1")).thenReturn(Optional.of(rotatingSession));
        when(userSessionRepository.findById(replacementId)).thenReturn(Optional.of(replacementSession));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        identityService.logout("refresh-token-1");

        assertEquals(UserSessionState.REVOKED, rotatingSession.getSessionState());
        assertEquals(UserSessionState.REVOKED, replacementSession.getSessionState());
        assertEquals(true, rotatingSession.isRevoked());
        assertEquals(true, replacementSession.isRevoked());
        verify(userSessionRepository, times(2)).save(any(UserSession.class));
    }

    @Test
    void refreshToken_shouldRevokeReplacementChainAfterGraceExpiry() {
        UserSession rotatingSession = new UserSession();
        rotatingSession.setId(UUID.randomUUID());
        rotatingSession.setUser(user);
        rotatingSession.setRefreshTokenHash("hash-1");
        rotatingSession.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        rotatingSession.setRevoked(false);
        rotatingSession.setSessionState(UserSessionState.ROTATING_GRACE);
        rotatingSession.setGraceExpiresAt(LocalDateTime.now().minusSeconds(1));
        rotatingSession.setGraceReplacementSessionId(UUID.randomUUID());

        UserSession replacementSession = new UserSession();
        replacementSession.setId(rotatingSession.getGraceReplacementSessionId());
        replacementSession.setUser(user);
        replacementSession.setRefreshTokenHash("hash-2");
        replacementSession.setExpiresAt(LocalDateTime.now().plusHours(1));
        replacementSession.setRevoked(false);
        replacementSession.setSessionState(UserSessionState.ACTIVE);

        when(jwtTokenProvider.hashToken("refresh-token-1")).thenReturn("hash-1");
        when(userSessionRepository.findByRefreshTokenHashForUpdate("hash-1")).thenReturn(Optional.of(rotatingSession));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSessionRepository.findById(rotatingSession.getGraceReplacementSessionId())).thenReturn(Optional.of(replacementSession));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var ex = assertThrows(com.fptu.exe.skillswap.shared.exception.BaseException.class,
                () -> identityService.refreshToken("refresh-token-1"));

        assertEquals(com.fptu.exe.skillswap.shared.exception.ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
        assertTrue(rotatingSession.isRevoked());
        assertEquals(UserSessionState.EXPIRED, rotatingSession.getSessionState());
        assertTrue(replacementSession.isRevoked());
        assertEquals(UserSessionState.REVOKED, replacementSession.getSessionState());
        verify(userSessionRepository, times(2)).save(any(UserSession.class));
    }
}
