package com.fptu.exe.skillswap.modules.identity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.session.domain.Session;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarApiClient {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String CALENDAR_BASE_URL = "https://www.googleapis.com/calendar/v3/calendars";

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final GoogleCalendarDateTimeMapper dateTimeMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode, String redirectUri, String codeVerifier) {
        ensureGoogleOauthConfigured();
        return sendForm(
                TOKEN_ENDPOINT,
                Map.of(
                        "code", authorizationCode,
                        "client_id", jwtProperties.getGoogle().getClientId(),
                        "client_secret", jwtProperties.getGoogle().getClientSecret(),
                        "redirect_uri", redirectUri,
                        "code_verifier", codeVerifier,
                        "grant_type", "authorization_code"
                )
        );
    }

    public GoogleTokenResponse refreshAccessToken(String refreshToken) {
        ensureGoogleOauthConfigured();
        return sendForm(
                TOKEN_ENDPOINT,
                Map.of(
                        "refresh_token", refreshToken,
                        "client_id", jwtProperties.getGoogle().getClientId(),
                        "client_secret", jwtProperties.getGoogle().getClientSecret(),
                        "grant_type", "refresh_token"
                )
        );
    }

    public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
        Map<String, Object> payload = sendJson(
                HttpRequest.newBuilder(URI.create(USERINFO_ENDPOINT))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build(),
                new TypeReference<>() {
                }
        );
        return new GoogleUserInfoResponse(
                stringValue(payload.get("sub")),
                stringValue(payload.get("email")),
                stringValue(payload.get("name")),
                stringValue(payload.get("picture")),
                Boolean.parseBoolean(stringValue(payload.get("email_verified")))
        );
    }

    public GoogleCalendarEventResponse createBookingEvent(String accessToken,
                                                          String calendarId,
                                                          String requestId,
                                                          Booking booking,
                                                          Session session) {
        Map<String, Object> body = buildBaseEventBody(booking, session);
        body.put("conferenceData", Map.of(
                "createRequest", Map.of(
                        "requestId", requestId,
                        "conferenceSolutionKey", Map.of("type", "hangoutsMeet")
                )
        ));
        String url = CALENDAR_BASE_URL + "/" + urlEncode(calendarId) + "/events?conferenceDataVersion=1&sendUpdates=all";
        return sendCalendarWrite(accessToken, url, "POST", body);
    }

    public GoogleCalendarEventResponse updateBookingEvent(String accessToken,
                                                          String calendarId,
                                                          String eventId,
                                                          Booking booking,
                                                          Session session) {
        Map<String, Object> body = buildBaseEventBody(booking, session);
        String url = CALENDAR_BASE_URL + "/" + urlEncode(calendarId) + "/events/" + urlEncode(eventId) + "?sendUpdates=all";
        return sendCalendarWrite(accessToken, url, "PUT", body);
    }

    public void cancelBookingEvent(String accessToken, String calendarId, String eventId) {
        String url = CALENDAR_BASE_URL + "/" + urlEncode(calendarId) + "/events/" + urlEncode(eventId) + "?sendUpdates=all";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
        sendNoContent(request);
    }

    public void revokeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(REVOKE_ENDPOINT))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("token=" + urlEncode(token)))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.warn("Best-effort Google token revoke failed: {}", ex.getMessage());
        }
    }

    private Map<String, Object> buildBaseEventBody(Booking booking, Session session) {
        String serviceTitle = booking.getServiceTitleSnapshot() != null
                ? booking.getServiceTitleSnapshot()
                : booking.getLearningGoalTitle();
        String description = """
                SkillSwap booking

                Mentee: %s
                Mentor: %s
                Mục tiêu: %s
                Mô tả: %s
                """.formatted(
                booking.getMentee() == null ? "N/A" : defaultText(booking.getMentee().getFullName(), booking.getMentee().getEmail()),
                booking.getMentorProfile() == null || booking.getMentorProfile().getUser() == null
                        ? "N/A"
                        : defaultText(booking.getMentorProfile().getUser().getFullName(), booking.getMentorProfile().getUser().getEmail()),
                defaultText(booking.getLearningGoalTitle(), "Mentoring session"),
                defaultText(booking.getLearningGoalDescription(), "Không có mô tả thêm")
        );
        List<Map<String, String>> attendees = new ArrayList<>();
        if (booking.getMentee() != null && StringUtils.hasText(booking.getMentee().getEmail())) {
            attendees.add(Map.of("email", booking.getMentee().getEmail()));
        }
        return new LinkedHashMap<>(Map.of(
                "summary", defaultText(serviceTitle, "SkillSwap Mentoring Session"),
                "description", description,
                "start", dateTimeMapper.toGoogleDateTime(session.getScheduledStartTime()),
                "end", dateTimeMapper.toGoogleDateTime(session.getScheduledEndTime()),
                "attendees", attendees
        ));
    }

    private GoogleTokenResponse sendForm(String url, Map<String, String> formFields) {
        String body = formFields.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        Map<String, Object> payload = sendJson(request, new TypeReference<>() {
        });
        return new GoogleTokenResponse(
                stringValue(payload.get("access_token")),
                stringValue(payload.get("refresh_token")),
                payload.get("expires_in") == null ? null : Long.valueOf(String.valueOf(payload.get("expires_in"))),
                stringValue(payload.get("scope")),
                stringValue(payload.get("id_token"))
        );
    }

    private GoogleCalendarEventResponse sendCalendarWrite(String accessToken,
                                                          String url,
                                                          String method,
                                                          Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();
            Map<String, Object> payload = sendJson(request, new TypeReference<>() {
            });
            String meetUrl = stringValue(payload.get("hangoutLink"));
            if (!StringUtils.hasText(meetUrl) && payload.get("conferenceData") instanceof Map<?, ?> conferenceData) {
                Object entryPoints = conferenceData.get("entryPoints");
                if (entryPoints instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> entry && "video".equals(String.valueOf(entry.get("entryPointType")))) {
                            meetUrl = stringValue(entry.get("uri"));
                            break;
                        }
                    }
                }
            }
            return new GoogleCalendarEventResponse(
                    stringValue(payload.get("id")),
                    stringValue(payload.get("etag")),
                    meetUrl
            );
        } catch (IOException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể tạo payload Google Calendar");
        }
    }

    private void sendNoContent(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return;
            }
            throw buildGoogleApiException(response.statusCode(), response.body());
        } catch (GoogleCalendarApiException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GoogleCalendarTransientException("GOOGLE_CALENDAR_TRANSPORT", "Không thể kết nối Google Calendar");
        }
    }

    private <T> T sendJson(HttpRequest request, TypeReference<T> typeReference) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), typeReference);
            }
            throw buildGoogleApiException(response.statusCode(), response.body());
        } catch (GoogleCalendarApiException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GoogleCalendarTransientException("GOOGLE_CALENDAR_TRANSPORT", "Không thể kết nối Google Calendar");
        }
    }

    private GoogleCalendarApiException buildGoogleApiException(int statusCode, String responseBody) {
        String errorCode = "GOOGLE_CALENDAR_ERROR_" + statusCode;
        String message = responseBody;
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
            if (payload.get("error") instanceof Map<?, ?> error) {
                Object code = error.get("status");
                Object detail = error.get("message");
                if (code != null) {
                    errorCode = String.valueOf(code);
                }
                if (detail != null) {
                    message = String.valueOf(detail);
                }
            } else if (payload.get("error") instanceof String errorValue) {
                errorCode = errorValue;
                Object errorDescription = payload.get("error_description");
                if (errorDescription != null) {
                    message = String.valueOf(errorDescription);
                }
            }
        } catch (Exception ignored) {
            // Keep raw response body
        }
        if (statusCode == 429 || statusCode >= 500) {
            return new GoogleCalendarTransientException(errorCode, message);
        }
        return new GoogleCalendarApiException(errorCode, message, statusCode);
    }

    private void ensureGoogleOauthConfigured() {
        if (!StringUtils.hasText(jwtProperties.getGoogle().getClientId())
                || !StringUtils.hasText(jwtProperties.getGoogle().getClientSecret())) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình GOOGLE_CLIENT_ID hoặc GOOGLE_CLIENT_SECRET");
        }
    }

    private String defaultText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record GoogleTokenResponse(
            String accessToken,
            String refreshToken,
            Long expiresInSeconds,
            String scope,
            String idToken
    ) {
    }

    public record GoogleUserInfoResponse(
            String subject,
            String email,
            String name,
            String picture,
            boolean emailVerified
    ) {
    }

    public record GoogleCalendarEventResponse(
            String eventId,
            String etag,
            String googleMeetUrl
    ) {
    }

    public static class GoogleCalendarApiException extends RuntimeException {
        private final String errorCode;
        private final int httpStatus;

        public GoogleCalendarApiException(String errorCode, String message, int httpStatus) {
            super(message);
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }

    public static class GoogleCalendarTransientException extends GoogleCalendarApiException {
        public GoogleCalendarTransientException(String errorCode, String message) {
            super(errorCode, message, 503);
        }
    }
}
