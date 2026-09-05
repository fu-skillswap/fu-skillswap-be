package com.fptu.exe.skillswap.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SecurityErrorResponseHandlerTest {

    @AfterEach
    void clearCorrelationContext() {
        com.fptu.exe.skillswap.shared.util.TraceContext.clear();
    }

    @Test
    void securityFailureUsesTheCommonApiEnvelope() throws Exception {
        SecurityErrorResponseHandler handler = new SecurityErrorResponseHandler(
                new ObjectMapper().findAndRegisterModules());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.writeError(response, ErrorCode.USER_BANNED);

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(403, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals(ErrorCode.USER_BANNED.getCode(), body.get("code").asText());
        assertEquals(ErrorCode.USER_BANNED.getMessage(), body.get("message").asText());
        assertEquals(403, body.get("status").asInt());
        assertNotNull(body.get("timestamp"));
        assertNotNull(response.getHeader("X-Correlation-ID"));
        assertEquals(response.getHeader("X-Correlation-ID"), response.getHeader("X-Request-Id"));
    }

    @Test
    void authenticationEntryPointUsesTheCommonApiEnvelope() throws Exception {
        SecurityErrorResponseHandler handler = new SecurityErrorResponseHandler(
                new ObjectMapper().findAndRegisterModules());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(null, response, new InsufficientAuthenticationException("missing token"));

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(401, response.getStatus());
        assertEquals(ErrorCode.UNAUTHENTICATED.getCode(), body.get("code").asText());
        assertEquals(ErrorCode.UNAUTHENTICATED.getMessage(), body.get("message").asText());
    }

    @Test
    void expiredAuthenticationUsesTheStableSessionExpiredCode() throws Exception {
        SecurityErrorResponseHandler handler = new SecurityErrorResponseHandler(
                new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.setAttribute(SecurityErrorResponseHandler.AUTHENTICATION_FAILURE_CODE_ATTRIBUTE,
                ErrorCode.SESSION_EXPIRED.getCode());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new InsufficientAuthenticationException("expired"));

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(401, response.getStatus());
        assertEquals(ErrorCode.SESSION_EXPIRED.getCode(), body.get("code").asText());
        assertEquals(ErrorCode.SESSION_EXPIRED.getMessage(), body.get("message").asText());
    }

    @Test
    void clientCorrelationIdIsPreservedAndSecurityDetailsStayOutOfBody() throws Exception {
        SecurityErrorResponseHandler handler = new SecurityErrorResponseHandler(
                new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.addHeader("X-Correlation-ID", "security-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response,
                new InsufficientAuthenticationException("JWT secret and refresh token must not be exposed"));

        String body = response.getContentAsString();
        assertEquals("security-abc-123", response.getHeader("X-Correlation-ID"));
        assertEquals("security-abc-123", response.getHeader("X-Request-Id"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("JWT secret"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("refresh token"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("correlationId"));
    }
}
