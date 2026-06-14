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
import com.fptu.exe.skillswap.modules.identity.service.IdentityLoginTransactionService;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import com.fptu.exe.skillswap.shared.event.ProfileStatusQuery;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private IdentityLoginTransactionService identityLoginTransactionService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        TokenResponse expected = TokenResponse.builder()
                .accessToken("access_token")
                .refreshToken("refresh_token")
                .build();

        when(googleAuthService.verifyToken("valid_token")).thenReturn(googleUserInfo);
        when(identityLoginTransactionService.loginWithVerifiedGoogleUser(googleUserInfo)).thenReturn(expected);

        TokenResponse tokenResponse = identityService.loginWithGoogle(request);

        assertNotNull(tokenResponse);
        assertEquals("access_token", tokenResponse.getAccessToken());
        assertEquals("refresh_token", tokenResponse.getRefreshToken());
        verify(googleAuthService, times(1)).verifyToken("valid_token");
        verify(identityLoginTransactionService, times(1)).loginWithVerifiedGoogleUser(googleUserInfo);
    }

    @Test
    void loginWithGoogle_transactionServiceThrowsBaseException_shouldPropagate() {
        GoogleLoginRequest request = new GoogleLoginRequest("valid_token");
        BaseException expected = new BaseException(ErrorCode.USER_BANNED, "Tài khoản của bạn đã bị khóa");

        when(googleAuthService.verifyToken("valid_token")).thenReturn(googleUserInfo);
        when(identityLoginTransactionService.loginWithVerifiedGoogleUser(googleUserInfo)).thenThrow(expected);

        BaseException exception = assertThrows(BaseException.class, () -> identityService.loginWithGoogle(request));
        assertEquals(ErrorCode.USER_BANNED, exception.getErrorCode());
        assertEquals("Tài khoản của bạn đã bị khóa", exception.getMessage());
    }

    @Test
    void loginWithGoogle_googleVerificationFails_shouldNotOpenTransaction() {
        GoogleLoginRequest request = new GoogleLoginRequest("valid_token");
        BaseException expected = new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Xác thực Google ID Token thất bại");

        when(googleAuthService.verifyToken("valid_token")).thenThrow(expected);

        BaseException exception = assertThrows(BaseException.class, () -> identityService.loginWithGoogle(request));
        assertEquals(ErrorCode.OAUTH_VERIFICATION_FAILED, exception.getErrorCode());
        verifyNoInteractions(identityLoginTransactionService);
    }

    @Test
    void loginWithGoogle_softDeletedUser_shouldDelegateToTransactionService() {
        GoogleLoginRequest request = new GoogleLoginRequest("valid_token");
        TokenResponse expected = TokenResponse.builder()
                .accessToken("access_token")
                .refreshToken("refresh_token")
                .build();

        when(googleAuthService.verifyToken("valid_token")).thenReturn(googleUserInfo);
        when(identityLoginTransactionService.loginWithVerifiedGoogleUser(googleUserInfo)).thenReturn(expected);

        TokenResponse tokenResponse = identityService.loginWithGoogle(request);

        assertNotNull(tokenResponse);
        assertEquals("access_token", tokenResponse.getAccessToken());
        verify(identityLoginTransactionService, times(1)).loginWithVerifiedGoogleUser(googleUserInfo);
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
        when(userRepository.findById(activeUserId)).thenReturn(Optional.of(activeUser));

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
    void refreshToken_deletedUser_shouldThrowException() {
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
        when(userRepository.findById(activeUserId)).thenReturn(Optional.empty());

        BaseException exception = assertThrows(BaseException.class, () -> identityService.refreshToken(request));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        assertEquals("Tài khoản liên kết đã bị xóa khỏi hệ thống", exception.getMessage());
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

    // ===== getCurrentUser tests =====

    @Test
    void getCurrentUser_userWithProfile_shouldReturnProfileCompletedTrue() {
        // Arrange
        when(userRepository.findById(activeUserId)).thenReturn(Optional.of(activeUser));
        when(userRoleRepository.findRoleCodesByUserId(activeUserId)).thenReturn(List.of());

        // Giả lập academic module set hasStudentProfile = true khi xử lý event
        doAnswer(invocation -> {
            ProfileStatusQuery query = invocation.getArgument(0);
            query.setHasStudentProfile(true);
            return null;
        }).when(eventPublisher).publishEvent(any(ProfileStatusQuery.class));

        // Act
        UserMeResponse response = identityService.getCurrentUser(activeUserId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isProfileCompleted(), "profileCompleted phải là true khi đã có StudentProfile");
        assertTrue(response.isHasStudentProfile(), "hasStudentProfile phải là true");
        assertEquals(activeUser.getEmail(), response.getEmail());
        verify(eventPublisher, times(1)).publishEvent(any(ProfileStatusQuery.class));
    }

    @Test
    void getCurrentUser_userWithoutProfile_shouldReturnProfileCompletedFalse() {
        // Arrange
        when(userRepository.findById(activeUserId)).thenReturn(Optional.of(activeUser));
        when(userRoleRepository.findRoleCodesByUserId(activeUserId)).thenReturn(List.of());

        // eventPublisher.publishEvent() không làm gì → query giữ giá trị mặc định false
        doNothing().when(eventPublisher).publishEvent(any(ProfileStatusQuery.class));

        // Act
        UserMeResponse response = identityService.getCurrentUser(activeUserId);

        // Assert
        assertNotNull(response);
        assertFalse(response.isProfileCompleted(), "profileCompleted phải là false khi chưa có StudentProfile");
        assertFalse(response.isHasStudentProfile(), "hasStudentProfile phải là false");
        verify(eventPublisher, times(1)).publishEvent(any(ProfileStatusQuery.class));
    }

    @Test
    void getCurrentUser_userNotFound_shouldThrowException() {
        when(userRepository.findById(activeUserId)).thenReturn(Optional.empty());

        BaseException exception = assertThrows(BaseException.class,
                () -> identityService.getCurrentUser(activeUserId));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}
