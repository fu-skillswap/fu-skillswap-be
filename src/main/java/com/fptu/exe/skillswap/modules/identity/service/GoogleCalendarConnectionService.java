package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnection;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnectionStatus;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarSyncStatus;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleCalendarConnectRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleCalendarStatusResponse;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarConnectionRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleCalendarConnectionService {

    private final UserRepository userRepository;
    private final GoogleCalendarConnectionRepository connectionRepository;
    private final GoogleCalendarApiClient googleCalendarApiClient;
    private final GoogleTokenCryptoService googleTokenCryptoService;
    private final GoogleAuthService googleAuthService;

    @Transactional
    public GoogleCalendarStatusResponse connect(UUID userId, GoogleCalendarConnectRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        if (!user.getRoles().contains(RoleCode.MENTOR)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentor mới có thể kết nối Google Calendar");
        }

        GoogleCalendarApiClient.GoogleTokenResponse tokenResponse =
                googleCalendarApiClient.exchangeAuthorizationCode(request.authorizationCode(), request.redirectUri(), request.codeVerifier());
        if (!StringUtils.hasText(tokenResponse.scope())
                || !tokenResponse.scope().contains("https://www.googleapis.com/auth/calendar")) {
            throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Google chưa cấp quyền Google Calendar cho tài khoản này");
        }
        GoogleCalendarApiClient.GoogleUserInfoResponse userInfo = googleCalendarApiClient.fetchUserInfo(tokenResponse.accessToken());
        if (!userInfo.emailVerified() || !StringUtils.hasText(userInfo.email())) {
            throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Tài khoản Google Calendar chưa có email xác thực");
        }

        GoogleCalendarConnection connection = connectionRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> GoogleCalendarConnection.builder().user(user).build());
        connection.setGoogleSubject(userInfo.subject());
        connection.setGoogleEmail(userInfo.email());
        connection.setCalendarId("primary");
        connection.setAccessTokenCiphertext(googleTokenCryptoService.encrypt(tokenResponse.accessToken()));
        connection.setRefreshTokenCiphertext(googleTokenCryptoService.encrypt(tokenResponse.refreshToken()));
        connection.setTokenExpiresAt(resolveTokenExpiry(tokenResponse.expiresInSeconds()));
        connection.setGrantedScopes(tokenResponse.scope());
        connection.setKeyVersion(googleTokenCryptoService.currentKeyVersion());
        connection.setConnectionStatus(GoogleCalendarConnectionStatus.ACTIVE);
        connection.setLastSyncErrorCode(null);
        connection.setLastSyncErrorMessage(null);
        connectionRepository.save(connection);
        return toStatusResponse(connection, true);
    }

    @Transactional(readOnly = true)
    public GoogleCalendarStatusResponse getStatus(UUID userId) {
        return connectionRepository.findByUserId(userId)
                .map(connection -> toStatusResponse(connection, true))
                .orElse(new GoogleCalendarStatusResponse(
                        false,
                        false,
                        null,
                        Collections.emptyList(),
                        false,
                        null,
                        null,
                        null,
                        null
                ));
    }

    @Transactional
    public GoogleCalendarStatusResponse disconnect(UUID userId) {
        GoogleCalendarConnection connection = connectionRepository.findByUserIdForUpdate(userId).orElse(null);
        if (connection == null) {
            return new GoogleCalendarStatusResponse(false, false, null, Collections.emptyList(), false, null, null, null, null);
        }
        String accessToken = decryptQuietly(connection.getAccessTokenCiphertext());
        String refreshToken = decryptQuietly(connection.getRefreshTokenCiphertext());
        googleCalendarApiClient.revokeToken(StringUtils.hasText(refreshToken) ? refreshToken : accessToken);

        connection.setConnectionStatus(GoogleCalendarConnectionStatus.REVOKED);
        connection.setAccessTokenCiphertext(googleTokenCryptoService.encrypt("revoked"));
        connection.setRefreshTokenCiphertext(null);
        connection.setTokenExpiresAt(null);
        connection.setLastSyncStatus(GoogleCalendarSyncStatus.REVOKED);
        connection.setLastSyncAt(DateTimeUtil.now());
        connection.setLastSyncErrorCode("GOOGLE_CALENDAR_REVOKED");
        connection.setLastSyncErrorMessage("Mentor đã ngắt kết nối Google Calendar.");
        connectionRepository.save(connection);
        return toStatusResponse(connection, false);
    }

    @Transactional(readOnly = true)
    public GoogleCalendarConnection getConnection(UUID userId) {
        return connectionRepository.findByUserId(userId).orElse(null);
    }

    @Transactional
    public GoogleCalendarConnection getActiveConnectionForSync(UUID userId) {
        return connectionRepository.findByUserIdForUpdate(userId).orElse(null);
    }

    @Transactional
    public String resolveAccessTokenForSync(GoogleCalendarConnection connection) {
        if (connection == null) {
            return null;
        }
        if (connection.getConnectionStatus() != GoogleCalendarConnectionStatus.ACTIVE) {
            throw new GoogleCalendarApiClient.GoogleCalendarApiException(
                    "GOOGLE_CALENDAR_NOT_ACTIVE",
                    "Google Calendar connection không còn active",
                    409
            );
        }
        if (connection.getTokenExpiresAt() == null || connection.getTokenExpiresAt().isAfter(DateTimeUtil.now().plusMinutes(2))) {
            return googleTokenCryptoService.decrypt(connection.getAccessTokenCiphertext());
        }

        String refreshToken = googleTokenCryptoService.decrypt(connection.getRefreshTokenCiphertext());
        if (!StringUtils.hasText(refreshToken)) {
            connection.setConnectionStatus(GoogleCalendarConnectionStatus.REQUIRES_RECONNECT);
            connection.setLastSyncErrorCode("GOOGLE_CALENDAR_REQUIRES_RECONNECT");
            connection.setLastSyncErrorMessage("Refresh token của Google Calendar không còn khả dụng.");
            connectionRepository.save(connection);
            throw new GoogleCalendarApiClient.GoogleCalendarApiException(
                    "invalid_grant",
                    "Google Calendar cần được kết nối lại",
                    401
            );
        }
        GoogleCalendarApiClient.GoogleTokenResponse refreshed = googleCalendarApiClient.refreshAccessToken(refreshToken);
        if (!StringUtils.hasText(refreshed.accessToken())) {
            connection.setConnectionStatus(GoogleCalendarConnectionStatus.REQUIRES_RECONNECT);
            connection.setLastSyncErrorCode("GOOGLE_CALENDAR_REQUIRES_RECONNECT");
            connection.setLastSyncErrorMessage("Không thể làm mới access token Google Calendar.");
            connectionRepository.save(connection);
            throw new GoogleCalendarApiClient.GoogleCalendarApiException(
                    "invalid_grant",
                    "Google Calendar cần được kết nối lại",
                    401
            );
        }
        connection.setAccessTokenCiphertext(googleTokenCryptoService.encrypt(refreshed.accessToken()));
        if (StringUtils.hasText(refreshed.refreshToken())) {
            connection.setRefreshTokenCiphertext(googleTokenCryptoService.encrypt(refreshed.refreshToken()));
        }
        connection.setTokenExpiresAt(resolveTokenExpiry(refreshed.expiresInSeconds()));
        if (StringUtils.hasText(refreshed.scope())) {
            connection.setGrantedScopes(refreshed.scope());
        }
        connectionRepository.save(connection);
        return refreshed.accessToken();
    }

    public GoogleAuthService.GoogleUserInfo resolveUserInfoForLogin(GoogleLoginRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu đăng nhập Google");
        }
        if (StringUtils.hasText(request.getIdToken())) {
            return googleAuthService.verifyToken(request.getIdToken());
        }
        if (!StringUtils.hasText(request.getAuthorizationCode())
                || !StringUtils.hasText(request.getRedirectUri())
                || !StringUtils.hasText(request.getCodeVerifier())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cần cung cấp Google ID token hoặc authorization code flow đầy đủ");
        }
        GoogleCalendarApiClient.GoogleTokenResponse tokenResponse =
                googleCalendarApiClient.exchangeAuthorizationCode(
                        request.getAuthorizationCode(),
                        request.getRedirectUri(),
                        request.getCodeVerifier()
                );
        if (StringUtils.hasText(tokenResponse.idToken())) {
            return googleAuthService.verifyToken(tokenResponse.idToken());
        }
        GoogleCalendarApiClient.GoogleUserInfoResponse userInfo = googleCalendarApiClient.fetchUserInfo(tokenResponse.accessToken());
        if (!userInfo.emailVerified()) {
            throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Email Google chưa được xác thực");
        }
        return googleAuthService.fromOpenIdProfile(
                userInfo.subject(),
                userInfo.email(),
                userInfo.name(),
                userInfo.picture(),
                userInfo.emailVerified()
        );
    }

    public UserMeGoogleCalendarView getUserMeView(UUID userId) {
        GoogleCalendarConnection connection = connectionRepository.findByUserId(userId).orElse(null);
        if (connection == null) {
            return new UserMeGoogleCalendarView(false, false, null, false, null, null);
        }
        boolean connected = connection.getConnectionStatus() == GoogleCalendarConnectionStatus.ACTIVE;
        boolean needsReconnect = connection.getConnectionStatus() == GoogleCalendarConnectionStatus.REQUIRES_RECONNECT;
        return new UserMeGoogleCalendarView(
                connected,
                connected,
                connection.getGoogleEmail(),
                needsReconnect,
                connection.getLastSyncStatus() == null ? null : connection.getLastSyncStatus().name(),
                connection.getLastSyncAt()
        );
    }

    private GoogleCalendarStatusResponse toStatusResponse(GoogleCalendarConnection connection, boolean connectedOverride) {
        boolean connected = connectedOverride && connection.getConnectionStatus() == GoogleCalendarConnectionStatus.ACTIVE;
        boolean needsReconnect = connection.getConnectionStatus() == GoogleCalendarConnectionStatus.REQUIRES_RECONNECT;
        return new GoogleCalendarStatusResponse(
                connected,
                connected,
                connection.getGoogleEmail(),
                splitScopes(connection.getGrantedScopes()),
                needsReconnect,
                connection.getLastSyncStatus() == null ? null : connection.getLastSyncStatus().name(),
                connection.getLastSyncAt(),
                connection.getLastSyncErrorCode(),
                connection.getLastSyncErrorMessage()
        );
    }

    private List<String> splitScopes(String grantedScopes) {
        if (!StringUtils.hasText(grantedScopes)) {
            return Collections.emptyList();
        }
        return Arrays.stream(grantedScopes.trim().split("\\s+")).toList();
    }

    private LocalDateTime resolveTokenExpiry(Long expiresInSeconds) {
        if (expiresInSeconds == null || expiresInSeconds <= 0) {
            return null;
        }
        return DateTimeUtil.now().plusSeconds(expiresInSeconds);
    }

    private String decryptQuietly(String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            return null;
        }
        try {
            return googleTokenCryptoService.decrypt(ciphertext);
        } catch (Exception ex) {
            return null;
        }
    }

    public record UserMeGoogleCalendarView(
            boolean connected,
            boolean syncEnabled,
            String email,
            boolean needsReconnect,
            String lastSyncStatus,
            LocalDateTime lastSyncAt
    ) {
    }
}
