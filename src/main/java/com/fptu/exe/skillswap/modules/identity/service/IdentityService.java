package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.RefreshTokenRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserMeResponse;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.event.ProfileStatusQuery;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserSessionRepository userSessionRepository;
    private final GoogleAuthService googleAuthService;
    private final IdentityLoginTransactionService identityLoginTransactionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;

    public TokenResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleAuthService.GoogleUserInfo googleUser = googleAuthService.verifyToken(request.getIdToken());
        return identityLoginTransactionService.loginWithVerifiedGoogleUser(googleUser);
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

        // Publish event để hỏi module academic trạng thái hồ sơ (synchronous request-reply)
        ProfileStatusQuery profileQuery = new ProfileStatusQuery(user.getId());
        eventPublisher.publishEvent(profileQuery);
        boolean hasProfile = profileQuery.isHasStudentProfile();

        return UserMeResponse.builder()
                .publicId(user.getPublicId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .profileCompleted(hasProfile)
                .hasStudentProfile(hasProfile)
                .build();
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

}
