package com.fptu.exe.skillswap.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String AUTHENTICATION_FAILURE_CODE_ATTRIBUTE =
            SecurityErrorResponseHandler.class.getName() + ".authenticationFailureCode";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(jakarta.servlet.http.HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        if (request != null && ErrorCode.SESSION_EXPIRED.getCode().equals(
                request.getAttribute(AUTHENTICATION_FAILURE_CODE_ATTRIBUTE))) {
            errorCode = ErrorCode.SESSION_EXPIRED;
        }
        writeError(request, response, errorCode);
    }

    @Override
    public void handle(jakarta.servlet.http.HttpServletRequest request,
                       HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        writeError(request, response, ErrorCode.ACCESS_DENIED);
    }

    public void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        writeError(null, response, errorCode);
    }

    public void writeError(jakarta.servlet.http.HttpServletRequest request,
                           HttpServletResponse response,
                           ErrorCode errorCode) throws IOException {
        String correlationId = TraceContext.getCurrentTraceId();
        String requestCorrelationId = request == null ? null
                : request.getHeader(TraceContext.CORRELATION_ID_HEADER);
        String requestTraceId = request == null ? null
                : request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (StringUtils.hasText(requestCorrelationId) || StringUtils.hasText(requestTraceId)) {
            correlationId = TraceContext.resolveAndSet(requestCorrelationId, requestTraceId);
        } else if (correlationId == null) {
            correlationId = TraceContext.resolveAndSet(
                    null,
                    null);
        }
        response.setHeader(TraceContext.CORRELATION_ID_HEADER, correlationId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, correlationId);
        log.warn("Handled security error code={} status={} correlationId={} method={} path={}",
                errorCode.getCode(), errorCode.getStatus(), correlationId,
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI());

        ApiResponse<Object> body = ApiResponse.builder()
                .timestamp(DateTimeUtil.instantNow())
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        response.setStatus(errorCode.getStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
