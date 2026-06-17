package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.config.RefreshTokenCookieProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.RefreshTokenRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserMeResponse;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final GoogleAuthService googleAuthService;
    private final IdentityLoginTransactionService identityLoginTransactionService;
    private final AcademicService academicService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenCookieProperties refreshTokenCookieProperties;

    public TokenResponse loginWithGoogle(GoogleLoginRequest request) {
        if (request == null || request.getIdToken() == null || request.getIdToken().trim().isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Google ID token không được để trống");
        }
        GoogleAuthService.GoogleUserInfo googleUser = googleAuthService.verifyToken(request.getIdToken());
        return identityLoginTransactionService.loginWithVerifiedGoogleUser(googleUser);
    }

    @Transactional
    public TokenResponse refreshToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Refresh token không được để trống");
        }
        String hash = jwtTokenProvider.hashToken(rawRefreshToken);

        UserSession session = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ"));

        if (session.isRevoked()) {
            throw new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã bị thu hồi");
        }

        if (session.getExpiresAt().isBefore(DateTimeUtil.now())) {
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
        if (!StringUtils.hasText(rawRefreshToken)) {
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
        if (publicId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        User user = userRepository.findById(publicId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        List<RoleCode> roles = new java.util.ArrayList<>(user.getRoles());
        boolean hasProfile = academicService.hasCompletedStudentProfile(user.getId());

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
        List<String> roleNames = user.getRoles()
                .stream()
                .map(RoleCode::name)
                .toList();

        if (roleNames.isEmpty()) {
            roleNames = Collections.singletonList(RoleCode.MENTEE.name());
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), roleNames);
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        String hashedRefresh = jwtTokenProvider.hashToken(refreshToken);

        // Save session
        long refreshExpirationMs = jwtProperties.getJwt().getRefreshToken().getExpiration();
        LocalDateTime expiresAt = DateTimeUtil.now().plusNanos(refreshExpirationMs * 1_000_000);

        UserSession session = UserSession.builder()
                .user(user)
                .refreshTokenHash(hashedRefresh)
                .expiresAt(expiresAt)
                .isRevoked(false)
                .build();
        userSessionRepository.save(session);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .build();
    }

    public String buildRefreshTokenCookieValue(String refreshToken) {
        return buildRefreshTokenCookieValue(refreshToken, false);
    }

    public String buildRefreshTokenCookieValue(String refreshToken, boolean cleared) {
        long maxAge = cleared ? 0L : jwtProperties.getJwt().getRefreshToken().getExpiration() / 1000;
        boolean secure = refreshTokenCookieProperties.isSecure();
        return org.springframework.http.ResponseCookie.from(refreshTokenCookieProperties.getName(),
                        cleared ? "" : refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(refreshTokenCookieProperties.getSameSite())
                .path(refreshTokenCookieProperties.getPath())
                .maxAge(maxAge)
                .build()
                .toString();
    }

    public String getRefreshTokenCookieName() {
        return refreshTokenCookieProperties.getName();
    }

}




