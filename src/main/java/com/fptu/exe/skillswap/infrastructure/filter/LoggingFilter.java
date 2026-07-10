package com.fptu.exe.skillswap.infrastructure.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import com.fptu.exe.skillswap.shared.util.TraceContext;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = resolveRequestId(request.getHeader(TraceContext.TRACE_ID_HEADER));

        TraceContext.setCurrentTraceId(requestId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, requestId);

        long startTime = System.currentTimeMillis();
        log.info("START: [{}] {}", request.getMethod(), request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } catch (java.io.IOException | jakarta.servlet.ServletException e) {
            log.error("Request filtering error: {}", e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("END: [{}] {} - Status: {} - Time: {}ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration);
            TraceContext.clear();
        }
    }

    private String resolveRequestId(String requestIdHeader) {
        if (requestIdHeader == null || requestIdHeader.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }

        String sanitized = requestIdHeader
                .trim()
                .replaceAll("[^a-zA-Z0-9\\-_.]", "");

        if (sanitized.isEmpty()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }

        if (sanitized.length() > MAX_REQUEST_ID_LENGTH) {
            return sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
        }
        return sanitized;
    }
}

