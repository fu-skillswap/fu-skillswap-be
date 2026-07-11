package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketAuthHandshakeInterceptorTest {

    private final WebSocketAuthHandshakeInterceptor interceptor = new WebSocketAuthHandshakeInterceptor();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void beforeHandshake_withoutAuthentication_shouldAllowAndMarkUnauthenticated() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws")),
                new ServletServerHttpResponse(response),
                null,
                attributes
        );

        assertTrue(allowed);
        assertEquals(Boolean.FALSE, attributes.get(WebSocketAuthHandshakeInterceptor.AUTHENTICATED_ATTRIBUTE));
    }

    @Test
    void beforeHandshake_withInvalidPrincipal_shouldAllowAndMarkUnauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-string", null, List.of())
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws")),
                new ServletServerHttpResponse(response),
                null,
                attributes
        );

        assertTrue(allowed);
        assertEquals(Boolean.FALSE, attributes.get(WebSocketAuthHandshakeInterceptor.AUTHENTICATED_ATTRIBUTE));
    }

    @Test
    void beforeHandshake_withValidUserPrincipal_shouldAttachAttributes() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "ws@test.com", List.of(RoleCode.MENTEE));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws")),
                new ServletServerHttpResponse(response),
                null,
                attributes
        );

        assertTrue(allowed);
        assertEquals(userId, attributes.get(WebSocketAuthHandshakeInterceptor.USER_ID_ATTRIBUTE));
        assertEquals(principal, attributes.get(WebSocketAuthHandshakeInterceptor.USER_PRINCIPAL_ATTRIBUTE));
        assertEquals(Boolean.TRUE, attributes.get(WebSocketAuthHandshakeInterceptor.AUTHENTICATED_ATTRIBUTE));
    }
}
