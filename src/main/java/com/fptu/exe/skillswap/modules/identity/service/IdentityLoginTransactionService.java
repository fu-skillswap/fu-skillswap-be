package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.identity.domain.OauthAccount;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.repository.OauthAccountRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityLoginTransactionService {

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse loginWithVerifiedGoogleUser(GoogleAuthService.GoogleUserInfo googleUser) {
        User user = userRepository
                .findByOauthProviderAndProviderUserIdIncludingDeleted("google", googleUser.getSub())
                .map(this::reactivateIfDeleted)
                .orElseGet(() -> findByEmailOrRegister(googleUser));

        checkUserStatus(user);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return generateTokensAndCreateSession(user);
    }

    private User findByEmailOrRegister(GoogleAuthService.GoogleUserInfo googleUser) {
        return userRepository.findByEmailIncludingDeleted(googleUser.getEmail())
                .map(existingUser -> {
                    reactivateIfDeleted(existingUser);
                    createOauthAccount(existingUser, googleUser.getSub(), googleUser.getEmail());
                    return existingUser;
                })
                .orElseGet(() -> registerNewOauthUser(googleUser));
    }

    private User reactivateIfDeleted(User user) {
        if (user.getDeletedAt() != null) {
            log.info("Reactivating soft-deleted user from Google OAuth login: {}", user.getEmail());
            user.setDeletedAt(null);
            user.setStatus(UserStatus.ACTIVE);
        }
        return user;
    }

    private User registerNewOauthUser(GoogleAuthService.GoogleUserInfo googleUser) {
        User user = User.builder()
                .email(googleUser.getEmail())
                .fullName(googleUser.getName() != null ? googleUser.getName() : extractDefaultName(googleUser.getEmail()))
                .avatarUrl(googleUser.getPicture())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        UserRole userRole = UserRole.builder()
                .id(new UserRoleId(savedUser.getId(), RoleCode.MENTEE))
                .user(savedUser)
                .assignedAt(LocalDateTime.now())
                .build();
        userRoleRepository.save(userRole);

        createOauthAccount(savedUser, googleUser.getSub(), googleUser.getEmail());
        return savedUser;
    }

    private void createOauthAccount(User user, String providerUserId, String providerEmail) {
        OauthAccount oauthAccount = OauthAccount.builder()
                .user(user)
                .provider("google")
                .providerUserId(providerUserId)
                .providerEmail(providerEmail)
                .build();
        oauthAccountRepository.save(oauthAccount);
    }

    private void checkUserStatus(User user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BaseException(ErrorCode.USER_BANNED, "Tài khoản của bạn đã bị khóa");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BaseException(ErrorCode.USER_INACTIVE, "Tài khoản của bạn chưa hoạt động");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND, "Tài khoản đã bị xóa khỏi hệ thống");
        }
    }

    private TokenResponse generateTokensAndCreateSession(User user) {
        List<String> roleNames = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(ur -> ur.getId().getRole().name())
                .toList();

        if (roleNames.isEmpty()) {
            roleNames = Collections.singletonList(RoleCode.MENTEE.name());
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), roleNames);
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        String hashedRefresh = jwtTokenProvider.hashToken(refreshToken);

        long refreshExpirationMs = jwtProperties.getJwt().getRefreshToken().getExpiration();
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000);

        UserSession session = UserSession.builder()
                .user(user)
                .refreshTokenHash(hashedRefresh)
                .expiresAt(expiresAt)
                .isRevoked(false)
                .build();
        userSessionRepository.save(session);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private String extractDefaultName(String email) {
        if (email == null || !email.contains("@")) {
            return "Người dùng";
        }
        return email.split("@")[0];
    }
}
