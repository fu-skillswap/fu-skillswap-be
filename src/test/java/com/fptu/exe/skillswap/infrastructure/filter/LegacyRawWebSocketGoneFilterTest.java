package com.fptu.exe.skillswap.infrastructure.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.SecurityErrorResponseHandler;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRawWebSocketGoneFilterTest {

    private final LegacyRawWebSocketGoneFilter filter = new LegacyRawWebSocketGoneFilter(
            new SecurityErrorResponseHandler(new ObjectMapper().findAndRegisterModules()));

    @Test
    void shouldReturn410ForLegacyRawWebSocketPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setServletPath("/ws");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(410, response.getStatus());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(ErrorCode.LEGACY_WEBSOCKET_GONE.getCode(), body.get("code").asText());
        assertEquals(410, body.get("status").asInt());
        assertTrue(body.get("message").asText().contains("STOMP /ws-stomp"));
        org.junit.jupiter.api.Assertions.assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldPassThroughForStompEndpoint() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws-stomp");
        request.setServletPath("/ws-stomp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldSkipPreflightOptions() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/ws");
        request.setServletPath("/ws");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }
}
