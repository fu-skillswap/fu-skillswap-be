package com.fptu.exe.skillswap.infrastructure.filter;

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

    private final LegacyRawWebSocketGoneFilter filter = new LegacyRawWebSocketGoneFilter();

    @Test
    void shouldReturn410ForLegacyRawWebSocketPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setServletPath("/ws");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(410, response.getStatus());
        assertTrue(response.getContentAsString().contains("STOMP /ws-stomp"));
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
