package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GoogleCalendarExceptionHandlerTest {

    private final GoogleCalendarExceptionHandler handler = new GoogleCalendarExceptionHandler();

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    @Test
    void transientProviderFailureUsesStableEnvelopeWithoutLeakingProviderMessage() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleGoogleCalendarFailure(
                new GoogleCalendarApiClient.GoogleCalendarTransientException(
                        "provider-timeout", "provider response contains sensitive detail"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals(ErrorCode.GOOGLE_CALENDAR_PROVIDER_ERROR.getCode(), response.getBody().getCode());
        assertEquals(ErrorCode.GOOGLE_CALENDAR_PROVIDER_ERROR.getMessage(), response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("sensitive"));
        assertNotNull(response.getHeaders().getFirst(TraceContext.CORRELATION_ID_HEADER));
    }

    @Test
    void invalidGrantUsesExistingOAuthErrorCode() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleGoogleCalendarFailure(
                new GoogleCalendarApiClient.GoogleCalendarApiException(
                        "invalid_grant", "provider detail", 401));

        assertEquals(ErrorCode.OAUTH_VERIFICATION_FAILED.getStatus(), response.getStatusCode().value());
        assertEquals(ErrorCode.OAUTH_VERIFICATION_FAILED.getCode(), response.getBody().getCode());
        assertEquals(ErrorCode.OAUTH_VERIFICATION_FAILED.getMessage(), response.getBody().getMessage());
    }
}
