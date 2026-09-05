package com.fptu.exe.skillswap.infrastructure.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = TraceContext.resolveAndSet(
                request.getHeader(TraceContext.CORRELATION_ID_HEADER),
                request.getHeader(TraceContext.TRACE_ID_HEADER));

        response.setHeader(TraceContext.CORRELATION_ID_HEADER, requestId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, requestId);

        long startTime = System.currentTimeMillis();
        log.info("START: [{}] {} correlationId={}", request.getMethod(), request.getRequestURI(), requestId);

        try {
            filterChain.doFilter(request, response);
        } catch (java.io.IOException | jakarta.servlet.ServletException e) {
            log.error("Request filtering error correlationId={} method={} path={}",
                    requestId, request.getMethod(), request.getRequestURI(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("END: [{}] {} - Status: {} - Time: {}ms correlationId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration, requestId);
            TraceContext.clear();
        }
    }

}

