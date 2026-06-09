package com.fptu.exe.skillswap.modules.identity;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.identity.domain.*;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.RefreshTokenRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserMeResponse;
import com.fptu.exe.skillswap.modules.identity.repository.OauthAccountRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.identity.service.GoogleAuthService;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private IdentityService identityService;

    private GoogleAuthService.GoogleUserInfo googleUserInfo;
    private User activeUser;
    private UUID activeUserId;

    @BeforeEach
    void setUp() {
        googleUserInfo = new GoogleAuthService.GoogleUserInfo();
        googleUserInfo.setSub("google_123");
        googleUserInfo.setEmail("test@gmail.com");
        googleUserInfo.setName("Test User");
        googleUserInfo.setPicture("http://avatar.url");

        activeUserId = UUID.randomUUID();
        activeUser = User.builder()
                .id(activeUserId)
                .email("test@gmail.com")
                .fullName("Test User")
                .avatarUrl("http://avatar.url")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void loginWithGoogle_newUser_shouldCreateAndReturnTokens() {
        GoogleLoginRequest request = new GoogleLoginRequest("valid_token");
        
        when(googleAuthService.verifyToken("valid_token")).thenReturn(googleUserInfo);
        when(oauthAccountRepository.findByProviderAndProviderUserId("google", "google_123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        
        // Mock save user
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        // Mock token generation settings
        JwtProperties.Jwt jwtSetting = new JwtProperties.Jwt();
        jwtSetting.getRefreshToken().setExpiration(604800000L);
        when(jwtProperties.getJwt()).thenReturn(jwtSetting);

        when(jwtTokenProvider.generateAccessToken(any(UUID.class), anyString(), anyList()))
                .thenReturn("access_token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh_token");
        when(jwtTokenProvider.hashToken("refresh_token")).thenReturn("refresh_token_hash");

        TokenResponse tokenResponse = identityService.loginWithGoogle(request);

        assertNotNull(tokenResponse);
        assertEquals("access_token", tokenResponse.getAccessToken());
        assertEquals("refresh_token", tokenResponse.getRefreshToken());
        
        verify(userRepository, times(2)).save(any(User.class));
        verify(userRoleRepository, times(1)).save(any(UserRole.class));
        verify(oauthAccountRepository, times(1)).save(any(OauthAccount.class));
        verify(userSessionRepository, times(1)).save(any(UserSession.class));
    }

    @Test
    void loginWithGoogle_bannedUser_shouldThrowException() {
        GoogleLoginRequest request = new GoogleLoginRequest("valid_token");
        activeUser.setStatus(UserStatus.BANNED);

        when(googleAuthService.verifyToken("valid_token")).thenReturn(googleUserInfo);
        
        OauthAccount account = OauthAccount.builder()
                .user(activeUser)
                .provider("google")
                .providerUserId("google_123")
                .build();
        when(oauthAccountRepository.findByProviderAndProviderUserId("google", "google_123")).thenReturn(Optional.of(account));

        BaseException exception = assertThrows(BaseException.class, () -> identityService.loginWithGoogle(request));
        assertEquals(ErrorCode.USER_BANNED, exception.getErrorCode());
        assertEquals("Tài khoản của bạn đã bị khóa", exception.getMessage());
    }

    @Test
    void loginWithGoogle_inactiveUser_shouldThrowException() {
        GoogleLoginRequest request = new GoogleLoginRequest("valid_token");
        activeUser.setStatus(UserStatus.INACTIVE);

        when(googleAuthService.verifyToken("valid_token")).thenReturn(googleUserInfo);
        
        OauthAccount account = OauthAccount.builder()
                .user(activeUser)
                .provider("google")
                .providerUserId("google_123")
                .build();
        when(oauthAccountRepository.findByProviderAndProviderUserId("google", "google_123")).thenReturn(Optional.of(account));

        BaseException exception = assertThrows(BaseException.class, () -> identityService.loginWithGoogle(request));
        assertEquals(ErrorCode.USER_INACTIVE, exception.getErrorCode());
        assertEquals("Tài khoản của bạn chưa hoạt động", exception.getMessage());
    }

    @Test
    void refreshToken_validToken_shouldRotateAndReturnNewTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("raw_refresh_token");
        String hashedToken = "hashed_refresh_token";
        
        when(jwtTokenProvider.hashToken("raw_refresh_token")).thenReturn(hashedToken);
        
        UserSession session = UserSession.builder()
                .id(UUID.randomUUID())
                .user(activeUser)
                .refreshTokenHash(hashedToken)
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        
        when(userSessionRepository.findByRefreshTokenHash(hashedToken)).thenReturn(Optional.of(session));

        JwtProperties.Jwt jwtSetting = new JwtProperties.Jwt();
        jwtSetting.getRefreshToken().setExpiration(604800000L);
        when(jwtProperties.getJwt()).thenReturn(jwtSetting);

        when(jwtTokenProvider.generateAccessToken(any(UUID.class), anyString(), anyList()))
                .thenReturn("new_access_token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new_refresh_token");
        when(jwtTokenProvider.hashToken("new_refresh_token")).thenReturn("new_refresh_token_hash");

        TokenResponse tokenResponse = identityService.refreshToken(request);

        assertNotNull(tokenResponse);
        assertEquals("new_access_token", tokenResponse.getAccessToken());
        assertEquals("new_refresh_token", tokenResponse.getRefreshToken());
        
        assertTrue(session.isRevoked());
        verify(userSessionRepository, times(1)).save(session);
        verify(userSessionRepository, times(1)).save(argThat(UserSession::isRevoked));
    }

    @Test
    void logout_validToken_shouldRevokeSession() {
        String rawRefresh = "some_refresh_token";
        String hashed = "hashed_refresh_token";

        when(jwtTokenProvider.hashToken(rawRefresh)).thenReturn(hashed);

        UserSession session = UserSession.builder()
                .id(UUID.randomUUID())
                .refreshTokenHash(hashed)
                .isRevoked(false)
                .build();

        when(userSessionRepository.findByRefreshTokenHash(hashed)).thenReturn(Optional.of(session));

        identityService.logout(rawRefresh);

        assertTrue(session.isRevoked());
        verify(userSessionRepository, times(1)).save(session);
    }
}
