package com.fptu.exe.skillswap.modules.identity;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.config.SystemAdminProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.OauthAccountRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.identity.service.GoogleAuthService;
import com.fptu.exe.skillswap.modules.identity.service.IdentityLoginTransactionService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityLoginTransactionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private SystemAdminProperties systemAdminProperties;

    @InjectMocks
    private IdentityLoginTransactionService service;

    @Test
    void loginWithVerifiedGoogleUser_whitelistedEmail_shouldAssignSystemAdminRole() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("root@fpt.edu.vn")
                .fullName("Root Admin")
                .status(UserStatus.ACTIVE)
                .build();
        GoogleAuthService.GoogleUserInfo googleUser = googleUser("root@fpt.edu.vn");

        when(userRepository.findByOauthProviderAndProviderUserIdIncludingDeleted("google", "google-root"))
                .thenReturn(Optional.of(user));
        when(systemAdminProperties.getEmails()).thenReturn(List.of("ROOT@fpt.edu.vn"));
        when(userRoleRepository.existsById(new UserRoleId(userId, RoleCode.SYSTEM_ADMIN))).thenReturn(false);
        when(userRoleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE, RoleCode.SYSTEM_ADMIN));
        mockTokenGeneration();

        service.loginWithVerifiedGoogleUser(googleUser);

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(roleCaptor.capture());
        assertEquals(new UserRoleId(userId, RoleCode.SYSTEM_ADMIN), roleCaptor.getValue().getId());
    }

    @Test
    void loginWithVerifiedGoogleUser_nonWhitelistedEmail_shouldNotAssignSystemAdminRole() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@fpt.edu.vn")
                .fullName("Normal User")
                .status(UserStatus.ACTIVE)
                .build();
        GoogleAuthService.GoogleUserInfo googleUser = googleUser("user@fpt.edu.vn");

        when(userRepository.findByOauthProviderAndProviderUserIdIncludingDeleted("google", "google-root"))
                .thenReturn(Optional.of(user));
        when(systemAdminProperties.getEmails()).thenReturn(List.of("root@fpt.edu.vn"));
        when(userRoleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));
        mockTokenGeneration();

        service.loginWithVerifiedGoogleUser(googleUser);

        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    private GoogleAuthService.GoogleUserInfo googleUser(String email) {
        GoogleAuthService.GoogleUserInfo googleUser = new GoogleAuthService.GoogleUserInfo();
        googleUser.setSub("google-root");
        googleUser.setEmail(email);
        googleUser.setName("Root Admin");
        return googleUser;
    }

    private void mockTokenGeneration() {
        JwtProperties.Jwt jwt = new JwtProperties.Jwt();
        jwt.getRefreshToken().setExpiration(604800000L);
        when(jwtProperties.getJwt()).thenReturn(jwt);
        when(jwtTokenProvider.generateAccessToken(any(UUID.class), anyString(), anyList())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenProvider.hashToken("refresh-token")).thenReturn("refresh-token-hash");
    }
}
