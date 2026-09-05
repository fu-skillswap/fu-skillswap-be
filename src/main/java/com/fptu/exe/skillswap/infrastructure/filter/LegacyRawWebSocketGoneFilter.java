package com.fptu.exe.skillswap.infrastructure.filter;

import com.fptu.exe.skillswap.infrastructure.security.SecurityErrorResponseHandler;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LegacyRawWebSocketGoneFilter extends OncePerRequestFilter {

    private static final String LEGACY_WEBSOCKET_PATH = "/ws";
    private final SecurityErrorResponseHandler securityErrorResponseHandler;

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
            securityErrorResponseHandler.writeError(request, response, ErrorCode.LEGACY_WEBSOCKET_GONE);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
