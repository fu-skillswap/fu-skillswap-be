package com.fptu.exe.skillswap.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LegacyRawWebSocketGoneFilter extends OncePerRequestFilter {

    private static final String LEGACY_WEBSOCKET_PATH = "/ws";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getServletPath();
        if (requestPath != null
                && (LEGACY_WEBSOCKET_PATH.equals(requestPath) || requestPath.startsWith(LEGACY_WEBSOCKET_PATH + "/"))) {
            response.setStatus(HttpServletResponse.SC_GONE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"code":"WS_0410","message":"Raw WebSocket endpoint /ws da ngung ho tro. Vui long chuyen sang STOMP /ws-stomp."}
                    """);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
