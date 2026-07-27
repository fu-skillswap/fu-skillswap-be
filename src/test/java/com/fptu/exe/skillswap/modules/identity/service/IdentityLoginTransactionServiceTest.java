package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.config.SystemAdminProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.OauthAccountRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityLoginTransactionServiceTest {

    @Test
    void googleLoginDoesNotReactivateSoftDeletedAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        OauthAccountRepository oauthAccountRepository = mock(OauthAccountRepository.class);
        UserSessionRepository userSessionRepository = mock(UserSessionRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        JwtProperties jwtProperties = new JwtProperties();
        SystemAdminProperties systemAdminProperties = new SystemAdminProperties();
        IdentityLoginTransactionService service = new IdentityLoginTransactionService(
                userRepository, oauthAccountRepository, userSessionRepository, jwtTokenProvider,
                jwtProperties, systemAdminProperties);

        User deletedUser = User.builder()
                .id(UUID.randomUUID())
                .email("deleted@example.com")
                .status(UserStatus.ACTIVE)
                .deletedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(userRepository.findByOauthProviderAndProviderUserIdIncludingDeleted("google", "google-sub"))
                .thenReturn(Optional.of(deletedUser));

        GoogleAuthService.GoogleUserInfo googleUser = new GoogleAuthService.GoogleUserInfo();
        googleUser.setSub("google-sub");
        googleUser.setEmail("deleted@example.com");
        googleUser.setName("Deleted");

        assertThatThrownBy(() -> service.loginWithVerifiedGoogleUser(googleUser))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .satisfies(code -> assertThat(code).isEqualTo(com.fptu.exe.skillswap.shared.exception.ErrorCode.USER_NOT_FOUND));

        assertThat(deletedUser.getDeletedAt()).isNotNull();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(oauthAccountRepository, userSessionRepository, jwtTokenProvider);
    }
}
