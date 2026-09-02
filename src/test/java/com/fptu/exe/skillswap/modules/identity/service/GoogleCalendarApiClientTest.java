package com.fptu.exe.skillswap.modules.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingCalendarPort.BookingCalendarSnapshot;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GoogleCalendarApiClientTest {

    private MockWebServer mockWebServer;
    private GoogleApiProperties properties;
    private GoogleCalendarApiClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new GoogleApiProperties();
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setCalendarRedirectUri("http://localhost/redirect");

        String baseUrl = mockWebServer.url("").toString();
        // Point all endpoints to mock server
        properties.setTokenEndpoint(baseUrl + "token");
        properties.setRevokeEndpoint(baseUrl + "revoke");
        properties.setUserinfoEndpoint(baseUrl + "userinfo");
        properties.setCalendarBaseUrl(baseUrl + "calendars");

        objectMapper = new ObjectMapper();
        client = new GoogleCalendarApiClient(properties, objectMapper, new GoogleCalendarDateTimeMapper(), mock(UserRepository.class));
    }

    @AfterEach
    void tearDown() throws IOException {
        Thread.interrupted(); // Clear interrupted status from previous tests
        mockWebServer.shutdown();
    }

    @Test
    void exchangeAuthorizationCode_success() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"acc-123\",\"refresh_token\":\"ref-123\",\"expires_in\":3600,\"scope\":\"calendar\",\"id_token\":\"id-123\"}"));

        GoogleCalendarApiClient.GoogleTokenResponse response = client.exchangeAuthorizationCode("code-123", "http://localhost/redirect", "verifier-123");

        assertNotNull(response);
        assertEquals("acc-123", response.accessToken());
        assertEquals("ref-123", response.refreshToken());
        assertEquals(3600L, response.expiresInSeconds());

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/token", request.getPath());
        assertEquals("POST", request.getMethod());
        assertTrue(request.getBody().readUtf8().contains("code=code-123"));
    }

    @Test
    void fetchUserInfo_success() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sub\":\"sub-123\",\"email\":\"test@example.com\",\"name\":\"Test User\",\"picture\":\"http://pic.jpg\",\"email_verified\":\"true\"}"));

        GoogleCalendarApiClient.GoogleUserInfoResponse response = client.fetchUserInfo("acc-123");

        assertNotNull(response);
        assertEquals("sub-123", response.subject());
        assertEquals("test@example.com", response.email());
        assertEquals("Test User", response.name());
        assertTrue(response.emailVerified());

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/userinfo", request.getPath());
        assertEquals("Bearer acc-123", request.getHeader("Authorization"));
    }

    @Test
    void createBookingEvent_success() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"evt-123\",\"etag\":\"etag-123\",\"hangoutLink\":\"http://meet.google.com/abc\"}"));

        BookingCalendarSnapshot booking = new BookingCalendarSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Java mentoring", "Learn Java", "PRJ301 preparation", null,
                Instant.parse("2026-07-10T03:00:00Z"), Instant.parse("2026-07-10T04:00:00Z"),
                null, null, true, false
        );

        GoogleCalendarApiClient.GoogleCalendarEventResponse response = client.createBookingEvent(
                "acc-123", "primary", "req-123", booking
        );

        assertNotNull(response);
        assertEquals("evt-123", response.eventId());
        assertEquals("etag-123", response.etag());
        assertEquals("http://meet.google.com/abc", response.googleMeetUrl());

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().startsWith("/calendars/primary/events"));
        assertEquals("POST", request.getMethod());
    }

    @Test
    void googleApi_handles4xx5xxError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":400,\"message\":\"Invalid value\",\"status\":\"INVALID_ARGUMENT\"}}"));

        GoogleCalendarApiClient.GoogleCalendarApiException exception = assertThrows(
                GoogleCalendarApiClient.GoogleCalendarApiException.class,
                () -> client.fetchUserInfo("acc-123")
        );

        assertEquals("INVALID_ARGUMENT", exception.getErrorCode());
        assertEquals("Invalid value", exception.getMessage());
        assertEquals(400, exception.getHttpStatus());
    }

    @Test
    void googleApi_handlesBadPayload() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("invalid-json"));

        assertThrows(
                Exception.class,
                () -> client.fetchUserInfo("acc-123")
        );
    }
}
