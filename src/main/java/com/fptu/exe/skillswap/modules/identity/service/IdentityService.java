package com.fptu.exe.skillswap.modules.identity.service;

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
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityService {

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserSessionRepository userSessionRepository;
    private final GoogleAuthService googleAuthService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse loginWithGoogle(GoogleLoginRequest request) {
        // 1. Verify Google token info
        GoogleAuthService.GoogleUserInfo googleUser = googleAuthService.verifyToken(request.getIdToken());

        // 2. Find or auto-create account
        User user = userRepository
                .findByOauthProviderAndProviderUserIdIncludingDeleted("google", googleUser.getSub())
                .map(existingUser -> {
                    if (existingUser.getDeletedAt() != null) {
                        log.info("Reactivating soft-deleted user from Google OAuth login: {}", existingUser.getEmail());
                        existingUser.setDeletedAt(null);
                        existingUser.setStatus(UserStatus.ACTIVE);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    // Check if user already exists with the same email (including soft-deleted ones)
                    return userRepository.findByEmailIncludingDeleted(googleUser.getEmail())
                            .map(existingUser -> {
                                if (existingUser.getDeletedAt() != null) {
                                    log.info("Reactivating soft-deleted user by email link: {}", existingUser.getEmail());
                                    existingUser.setDeletedAt(null);
                                    existingUser.setStatus(UserStatus.ACTIVE);
                                }
                                // Link existing user to new OAuth account
                                createOauthAccount(existingUser, googleUser.getSub(), googleUser.getEmail());
                                return existingUser;
                            })
                            .orElseGet(() -> {
                                // Create brand new user
                                return registerNewOauthUser(googleUser);
                            });
                });

        // 3. Basic User Status Check (Block login if BANNED or INACTIVE)
        checkUserStatus(user);

        // 4. Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 5. Issue JWT (AccessToken & RefreshToken)
        return generateTokensAndCreateSession(user);
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String hash = jwtTokenProvider.hashToken(rawToken);

        UserSession session = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ"));

        if (session.isRevoked()) {
            throw new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã bị thu hồi");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã quá hạn");
        }

        UUID userId = session.getUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Tài khoản liên kết đã bị xóa khỏi hệ thống"));
        checkUserStatus(user);

        // Revoke the old session
        session.setRevoked(true);
        userSessionRepository.save(session);

        // Generate and issue new token pair (refresh token rotation)
        return generateTokensAndCreateSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.trim().isEmpty()) {
            return;
        }
        String hash = jwtTokenProvider.hashToken(rawRefreshToken);
        userSessionRepository.findByRefreshTokenHash(hash).ifPresent(session -> {
            session.setRevoked(true);
            userSessionRepository.save(session);
        });
    }

    @Transactional(readOnly = true)
    public UserMeResponse getCurrentUser(UUID publicId) {
        User user = userRepository.findById(publicId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        List<RoleCode> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(ur -> ur.getId().getRole())
                .toList();

        return UserMeResponse.builder()
                .publicId(user.getPublicId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .build();
    }

    private User registerNewOauthUser(GoogleAuthService.GoogleUserInfo googleUser) {
        User user = User.builder()
                .email(googleUser.getEmail())
                .fullName(googleUser.getName() != null ? googleUser.getName() : extractDefaultName(googleUser.getEmail()))
                .avatarUrl(googleUser.getPicture())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        // Assign default role MENTEE
        UserRoleId roleId = new UserRoleId(savedUser.getId(), RoleCode.MENTEE);
        UserRole userRole = UserRole.builder()
                .id(roleId)
                .user(savedUser)
                .assignedAt(LocalDateTime.now())
                .build();
        userRoleRepository.save(userRole);

        // Create OAuth account linkage
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

        // Save session
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
