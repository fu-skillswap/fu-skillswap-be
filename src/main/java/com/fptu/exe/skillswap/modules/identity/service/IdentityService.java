package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.infrastructure.config.RefreshTokenCookieProperties;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserSessionState;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
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
    private final GoogleCalendarConnectionService googleCalendarConnectionService;
    private final IdentityLoginTransactionService identityLoginTransactionService;
    private final RefreshTokenReplayCryptoService refreshTokenReplayCryptoService;
    private final AcademicService academicService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenCookieProperties refreshTokenCookieProperties;

    public TokenResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleAuthService.GoogleUserInfo googleUser = googleCalendarConnectionService.resolveUserInfoForLogin(request);
        return identityLoginTransactionService.loginWithVerifiedGoogleUser(googleUser);
    }

    @Transactional
    public TokenResponse refreshToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Refresh token không được để trống");
        }
        String hash = jwtTokenProvider.hashToken(rawRefreshToken);

        UserSession session = userSessionRepository.findByRefreshTokenHashForUpdate(hash)
                .orElseThrow(() -> new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ"));

        UserSessionState sessionState = resolveSessionState(session);

        if (session.isRevoked()) {
            throw new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã bị thu hồi");
        }

        LocalDateTime now = DateTimeUtil.now();

        if (sessionState == UserSessionState.ROTATING_GRACE) {
            UUID userId = session.getUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Tài khoản liên kết đã bị xóa khỏi hệ thống"));
            checkUserStatus(user);

            if (isGraceWindowActive(session, now)) {
                TokenResponse replayResponse = refreshTokenReplayCryptoService.decrypt(session.getGraceReplayCiphertext());
                if (replayResponse == null) {
                    revokeSessionFamily(session, UserSessionState.REVOKED);
                    throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể khôi phục phản hồi làm mới phiên");
                }
                if (isGraceReplacementRevoked(session, now)) {
                    revokeSessionFamily(session, UserSessionState.REVOKED);
                    throw new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã bị thu hồi");
                }
                return replayResponse;
            }
            revokeSessionFamily(session, UserSessionState.EXPIRED);
            throw new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã quá hạn");
        }

        if (session.getExpiresAt().isBefore(now)) {
            revokeSessionFamily(session, UserSessionState.EXPIRED);
            throw new BaseException(ErrorCode.SESSION_EXPIRED, "Phiên đăng nhập đã quá hạn");
        }

        UUID userId = session.getUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Tài khoản liên kết đã bị xóa khỏi hệ thống"));
        checkUserStatus(user);

        // Generate and issue new token pair (refresh token rotation)
        TokenIssuance issuance = generateTokensAndCreateSession(user);
        session.setSessionState(UserSessionState.ROTATING_GRACE);
        session.setGraceExpiresAt(now.plusNanos(jwtProperties.getJwt().getRefreshToken().getRotationGracePeriodMillis() * 1_000_000L));
        session.setGraceReplayCiphertext(refreshTokenReplayCryptoService.encrypt(issuance.tokenResponse()));
        session.setGraceReplacementSessionId(issuance.session().getId());
        userSessionRepository.save(session);
        return issuance.tokenResponse();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        String hash = jwtTokenProvider.hashToken(rawRefreshToken);
        userSessionRepository.findByRefreshTokenHashForUpdate(hash).ifPresent(session -> {
            revokeSessionFamily(session, UserSessionState.REVOKED);
            userSessionRepository.findByGraceReplacementSessionId(session.getId())
                    .ifPresent(parentSession -> revokeSession(parentSession, UserSessionState.REVOKED));
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
        GoogleCalendarConnectionService.UserMeGoogleCalendarView googleCalendarView =
                googleCalendarConnectionService.getUserMeView(user.getId());

        return UserMeResponse.builder()
                .publicId(user.getPublicId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .profileCompleted(hasProfile)
                .hasStudentProfile(hasProfile)
                .googleCalendarConnected(googleCalendarView.connected())
                .googleCalendarSyncEnabled(googleCalendarView.syncEnabled())
                .googleCalendarEmail(googleCalendarView.email())
                .googleCalendarNeedsReconnect(googleCalendarView.needsReconnect())
                .googleCalendarLastSyncStatus(googleCalendarView.lastSyncStatus())
                .googleCalendarLastSyncAt(googleCalendarView.lastSyncAt())
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

    private TokenIssuance generateTokensAndCreateSession(User user) {
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
                .sessionState(UserSessionState.ACTIVE)
                .build();
        UserSession savedSession = userSessionRepository.save(session);

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
        return new TokenIssuance(tokenResponse, savedSession);
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

    private UserSessionState resolveSessionState(UserSession session) {
        return session == null || session.getSessionState() == null
                ? UserSessionState.ACTIVE
                : session.getSessionState();
    }

    private boolean isGraceWindowActive(UserSession session, LocalDateTime now) {
        return session.getGraceExpiresAt() != null && !session.getGraceExpiresAt().isBefore(now);
    }

    private boolean isGraceReplacementRevoked(UserSession session, LocalDateTime now) {
        if (session.getGraceReplacementSessionId() == null) {
            return false;
        }
        return userSessionRepository.findById(session.getGraceReplacementSessionId())
                .map(replacement -> replacement.isRevoked() || replacement.getExpiresAt().isBefore(now))
                .orElse(false);
    }

    private void markSessionExpired(UserSession session) {
        session.setRevoked(true);
        session.setSessionState(UserSessionState.EXPIRED);
        session.setGraceExpiresAt(null);
        session.setGraceReplayCiphertext(null);
        session.setGraceReplacementSessionId(null);
    }

    private void markSessionRevoked(UserSession session) {
        session.setRevoked(true);
        session.setSessionState(UserSessionState.REVOKED);
        session.setGraceExpiresAt(null);
        session.setGraceReplayCiphertext(null);
        session.setGraceReplacementSessionId(null);
    }

    private void revokeSession(UserSession session, UserSessionState terminalState) {
        if (session == null) {
            return;
        }
        session.setRevoked(true);
        session.setSessionState(terminalState);
        session.setGraceExpiresAt(null);
        session.setGraceReplayCiphertext(null);
        session.setGraceReplacementSessionId(null);
        userSessionRepository.save(session);
    }

    private void revokeSessionFamily(UserSession session, UserSessionState terminalState) {
        if (session == null) {
            return;
        }
        UUID replacementSessionId = session.getGraceReplacementSessionId();
        revokeSession(session, terminalState);
        revokeSessionFamilyById(replacementSessionId);
    }

    private void revokeSessionFamilyById(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        userSessionRepository.findById(sessionId).ifPresent(childSession -> revokeSessionFamily(childSession, UserSessionState.REVOKED));
    }

    private record TokenIssuance(TokenResponse tokenResponse, UserSession session) {}
}




