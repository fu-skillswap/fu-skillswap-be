package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnection;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnectionStatus;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarSyncStatus;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleCalendarConnectRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleCalendarStatusResponse;
import com.fptu.exe.skillswap.modules.identity.port.GoogleCalendarConnectionPort;
import com.fptu.exe.skillswap.modules.identity.port.MentorCalendarEligibilityPort;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarConnectionRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarConnectionService implements GoogleCalendarConnectionPort {

    private final UserRepository userRepository;
    private final GoogleCalendarConnectionRepository connectionRepository;
    private final GoogleCalendarApiClient googleCalendarApiClient;
    private final GoogleTokenCryptoService googleTokenCryptoService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final GoogleOAuthStateService googleOAuthStateService;
    private final GoogleApiProperties googleApiProperties;
    private final MentorCalendarEligibilityPort mentorCalendarEligibilityPort;
    private BookingRepository bookingRepository;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Autowired(required = false)
    void setBookingRepository(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public GoogleAuthorizationContextResponse issueAuthorizationContext(
            UUID userId,
            String redirectUri,
            String codeChallenge
    ) {
        mentorCalendarEligibilityPort.requireVerifiedMentor(userId);
        validateCalendarRedirectUri(redirectUri);
        return googleOAuthStateService.issueCalendarConnect(userId, redirectUri, codeChallenge);
    }

    public GoogleCalendarStatusResponse connect(UUID userId, GoogleCalendarConnectRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu kết nối Google Calendar");
        }
        mentorCalendarEligibilityPort.requireVerifiedMentor(userId);
        validateCalendarRedirectUri(request.redirectUri());
        googleOAuthStateService.consumeCalendarConnect(
                userId,
                request.state(),
                request.redirectUri(),
                request.codeVerifier()
        );

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

        return transactionTemplate.execute(status -> {
            mentorCalendarEligibilityPort.requireVerifiedMentor(userId);
            User user = requireMentorUser(userId);
            GoogleCalendarConnection connection = connectionRepository.findByUserIdForUpdate(userId)
                    .orElseGet(() -> GoogleCalendarConnection.builder().user(user).build());
            connection.setGoogleSubject(userInfo.subject());
            connection.setGoogleEmail(userInfo.email());
            connection.setCalendarId("primary");
            connection.setAccessTokenCiphertext(googleTokenCryptoService.encrypt(tokenResponse.accessToken()));
            if (StringUtils.hasText(tokenResponse.refreshToken())) {
                connection.setRefreshTokenCiphertext(googleTokenCryptoService.encrypt(tokenResponse.refreshToken()));
            }
            connection.setTokenExpiresAt(resolveTokenExpiry(tokenResponse.expiresInSeconds()));
            connection.setGrantedScopes(tokenResponse.scope());
            connection.setKeyVersion(googleTokenCryptoService.currentKeyVersion());
            connection.setConnectionStatus(GoogleCalendarConnectionStatus.ACTIVE);
            connection.setLastSyncErrorCode(null);
            connection.setLastSyncErrorMessage(null);
            connectionRepository.save(connection);
            return toStatusResponse(connection, true);
        });
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

    public GoogleCalendarStatusResponse disconnect(UUID userId) {
        ConnectionRevocationSnapshot snapshot = transactionTemplate.execute(status -> {
            GoogleCalendarConnection connection = connectionRepository.findByUserIdForUpdate(userId).orElse(null);
            if (connection == null) {
                return null;
            }
            if (mentorCalendarEligibilityPort.hasActiveOneToOneService(userId)) {
                throw new BaseException(
                        ErrorCode.GOOGLE_CALENDAR_DISCONNECT_BLOCKED,
                        "Cần tắt toàn bộ dịch vụ mentoring trước khi ngắt Google Calendar"
                );
            }
            if (bookingRepository != null && bookingRepository.existsByMentorProfileUserIdAndStatusAndSelectedStartTimeUtcAfter(
                    userId, BookingStatus.PAID, timeProvider.instant())) {
                throw new BaseException(
                        ErrorCode.GOOGLE_CALENDAR_DISCONNECT_BLOCKED,
                        "Không thể ngắt Google Calendar khi còn booking đã thanh toán trong tương lai"
                );
            }
            ConnectionRevocationSnapshot result = new ConnectionRevocationSnapshot(
                    connection.getAccessTokenCiphertext(),
                    connection.getRefreshTokenCiphertext()
            );
            connection.setConnectionStatus(GoogleCalendarConnectionStatus.REVOKED);
            connection.setAccessTokenCiphertext(googleTokenCryptoService.encrypt("revoked"));
            connection.setRefreshTokenCiphertext(null);
            connection.setTokenExpiresAt(null);
            connection.setLastSyncStatus(GoogleCalendarSyncStatus.REVOKED);
            connection.setLastSyncAt(timeProvider.nowBusiness());
            connection.setLastSyncErrorCode("GOOGLE_CALENDAR_REVOKED");
            connection.setLastSyncErrorMessage("Mentor đã ngắt kết nối Google Calendar.");
            connectionRepository.save(connection);
            return result;
        });
        if (snapshot == null) {
            return new GoogleCalendarStatusResponse(false, false, null, Collections.emptyList(), false, null, null, null, null);
        }
        String accessToken = decryptQuietly(snapshot.accessTokenCiphertext());
        String refreshToken = decryptQuietly(snapshot.refreshTokenCiphertext());
        try {
            googleCalendarApiClient.revokeToken(StringUtils.hasText(refreshToken) ? refreshToken : accessToken);
        } catch (RuntimeException exception) {
            // DB đã chặn dùng token; lỗi revoke từ Google không được mở lại kết nối.
            log.warn("Không thể revoke Google Calendar token sau khi đã ngắt kết nối trong DB. userId={}", userId, exception);
        }
        return getStatus(userId);
    }

    private User requireMentorUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        if (!user.getRoles().contains(RoleCode.MENTOR)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentor mới có thể kết nối Google Calendar");
        }
        return user;
    }

    private record ConnectionRevocationSnapshot(String accessTokenCiphertext, String refreshTokenCiphertext) {
    }

    @Override
    @Transactional
    public void requireActiveConnectionForServiceCreation(UUID mentorUserId) {
        GoogleCalendarConnection connection = connectionRepository.findByUserIdForUpdate(mentorUserId)
                .orElseThrow(() -> new BaseException(
                        ErrorCode.GOOGLE_CALENDAR_CONNECTION_REQUIRED,
                        "Cần kết nối Google Calendar trước khi tạo hoặc bật dịch vụ mentoring"
                ));
        if (connection.getConnectionStatus() != GoogleCalendarConnectionStatus.ACTIVE) {
            throw new BaseException(
                    ErrorCode.GOOGLE_CALENDAR_CONNECTION_REQUIRED,
                    "Google Calendar chưa kết nối hoặc cần kết nối lại"
            );
        }
    }

    @Transactional(readOnly = true)
    public GoogleCalendarConnection getConnection(UUID userId) {
        return connectionRepository.findByUserId(userId).orElse(null);
    }

    @Transactional
    public GoogleCalendarConnection getActiveConnectionForSync(UUID userId) {
        return connectionRepository.findByUserIdForUpdate(userId).orElse(null);
    }

    public String resolveAccessTokenForSync(UUID connectionId) {
        if (connectionId == null) return null;

        // Transaction 1: Check token validity
        String validToken = transactionTemplate.execute(status -> {
            GoogleCalendarConnection conn = connectionRepository.findByIdForUpdate(connectionId).orElse(null);
            if (conn == null) return null;
            if (conn.getConnectionStatus() != GoogleCalendarConnectionStatus.ACTIVE) {
                throw new GoogleCalendarApiClient.GoogleCalendarApiException(
                        "GOOGLE_CALENDAR_NOT_ACTIVE",
                        "Google Calendar connection không còn active",
                        409
                );
            }
            if (conn.getTokenExpiresAt() == null || conn.getTokenExpiresAt().isAfter(timeProvider.nowBusiness().plusMinutes(2))) {
                return googleTokenCryptoService.decrypt(conn.getAccessTokenCiphertext());
            }
            return null; // Token is expired, need refresh
        });

        if (validToken != null) {
            return validToken;
        }

        // Token is expired. Fetch refresh token without lock
        GoogleCalendarConnection connection = connectionRepository.findById(connectionId).orElseThrow();
        String refreshToken = googleTokenCryptoService.decrypt(connection.getRefreshTokenCiphertext());
        if (!StringUtils.hasText(refreshToken)) {
            transactionTemplate.executeWithoutResult(status -> {
                GoogleCalendarConnection conn = connectionRepository.findById(connectionId).orElseThrow();
                conn.setConnectionStatus(GoogleCalendarConnectionStatus.REQUIRES_RECONNECT);
                conn.setLastSyncErrorCode("GOOGLE_CALENDAR_REQUIRES_RECONNECT");
                conn.setLastSyncErrorMessage("Refresh token của Google Calendar không còn khả dụng.");
                connectionRepository.save(conn);
            });
            throw new GoogleCalendarApiClient.GoogleCalendarApiException("invalid_grant", "Google Calendar cần được kết nối lại", 401);
        }

        // Network Call: Refresh token (NO TRANSACTION HELD)
        GoogleCalendarApiClient.GoogleTokenResponse refreshed = googleCalendarApiClient.refreshAccessToken(refreshToken);

        // Transaction 2: Update connection with new token
        return transactionTemplate.execute(status -> {
            GoogleCalendarConnection conn = connectionRepository.findByIdForUpdate(connectionId).orElseThrow();
            if (!StringUtils.hasText(refreshed.accessToken())) {
                conn.setConnectionStatus(GoogleCalendarConnectionStatus.REQUIRES_RECONNECT);
                conn.setLastSyncErrorCode("GOOGLE_CALENDAR_REQUIRES_RECONNECT");
                conn.setLastSyncErrorMessage("Không thể làm mới access token Google Calendar.");
                connectionRepository.save(conn);
                throw new GoogleCalendarApiClient.GoogleCalendarApiException("invalid_grant", "Google Calendar cần được kết nối lại", 401);
            }
            conn.setAccessTokenCiphertext(googleTokenCryptoService.encrypt(refreshed.accessToken()));
            if (StringUtils.hasText(refreshed.refreshToken())) {
                conn.setRefreshTokenCiphertext(googleTokenCryptoService.encrypt(refreshed.refreshToken()));
            }
            conn.setTokenExpiresAt(resolveTokenExpiry(refreshed.expiresInSeconds()));
            if (StringUtils.hasText(refreshed.scope())) {
                conn.setGrantedScopes(refreshed.scope());
            }
            connectionRepository.save(conn);
            return refreshed.accessToken();
        });
    }

    public UserMeGoogleCalendarView getUserMeView(UUID userId) {
        GoogleCalendarConnection connection = connectionRepository.findByUserId(userId).orElse(null);
        if (connection == null) {
            return new UserMeGoogleCalendarView(false, false, null, false, null, null);
        }
        boolean connected = connection.getConnectionStatus() == GoogleCalendarConnectionStatus.ACTIVE;
        boolean needsReconnect = connection.getConnectionStatus() == GoogleCalendarConnectionStatus.REQUIRES_RECONNECT;
        OffsetDateTime lastSyncAtOffset = connection.getLastSyncAtUtc() != null
                ? com.fptu.exe.skillswap.modules.booking.service.BookingTime.toOffsetDateTime(connection.getLastSyncAtUtc())
                : (connection.getLastSyncAt() != null ? com.fptu.exe.skillswap.modules.booking.service.BookingTime.toOffsetDateTime(connection.getLastSyncAt()) : null);
        return new UserMeGoogleCalendarView(
                connected,
                connected,
                connection.getGoogleEmail(),
                needsReconnect,
                connection.getLastSyncStatus() == null ? null : connection.getLastSyncStatus().name(),
                lastSyncAtOffset
        );
    }

    private GoogleCalendarStatusResponse toStatusResponse(GoogleCalendarConnection connection, boolean connectedOverride) {
        boolean connected = connectedOverride && connection.getConnectionStatus() == GoogleCalendarConnectionStatus.ACTIVE;
        boolean needsReconnect = connection.getConnectionStatus() == GoogleCalendarConnectionStatus.REQUIRES_RECONNECT;
        OffsetDateTime lastSyncAtOffset = connection.getLastSyncAtUtc() != null
                ? com.fptu.exe.skillswap.modules.booking.service.BookingTime.toOffsetDateTime(connection.getLastSyncAtUtc())
                : (connection.getLastSyncAt() != null ? com.fptu.exe.skillswap.modules.booking.service.BookingTime.toOffsetDateTime(connection.getLastSyncAt()) : null);
        return new GoogleCalendarStatusResponse(
                connected,
                connected,
                connection.getGoogleEmail(),
                splitScopes(connection.getGrantedScopes()),
                needsReconnect,
                connection.getLastSyncStatus() == null ? null : connection.getLastSyncStatus().name(),
                lastSyncAtOffset,
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
        return timeProvider.nowBusiness().plusSeconds(expiresInSeconds);
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

    private void validateCalendarRedirectUri(String requestRedirectUri) {
        String configuredRedirectUri = googleApiProperties.getCalendarRedirectUri();
        if (!StringUtils.hasText(configuredRedirectUri)) {
            throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "GOOGLE_CALENDAR_REDIRECT_URI chưa được cấu hình"
            );
        }
        if (!StringUtils.hasText(requestRedirectUri) || !configuredRedirectUri.equals(requestRedirectUri.trim())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "redirectUri không khớp với cấu hình Google Calendar của hệ thống");
        }
    }

    public record UserMeGoogleCalendarView(
            boolean connected,
            boolean syncEnabled,
            String email,
            boolean needsReconnect,
            String lastSyncStatus,
            OffsetDateTime lastSyncAt
    ) {
    }
}
