package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Maps Google Calendar provider failures without coupling shared error handling to Identity. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GoogleCalendarExceptionHandler {

    @ExceptionHandler(GoogleCalendarApiClient.GoogleCalendarApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleGoogleCalendarFailure(
            GoogleCalendarApiClient.GoogleCalendarApiException ex) {
        ErrorCode errorCode;
        if (ex instanceof GoogleCalendarApiClient.GoogleCalendarTransientException
                || ex.getHttpStatus() == 429 || ex.getHttpStatus() >= 500) {
            errorCode = ErrorCode.GOOGLE_CALENDAR_PROVIDER_ERROR;
        } else if (ex.getHttpStatus() == 401 || "invalid_grant".equalsIgnoreCase(ex.getErrorCode())) {
            errorCode = ErrorCode.OAUTH_VERIFICATION_FAILED;
        } else if (ex.getHttpStatus() == 409) {
            errorCode = ErrorCode.RESOURCE_CONFLICT;
        } else {
            errorCode = ErrorCode.BAD_REQUEST;
        }

        String correlationId = applyCorrelationHeaders();
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        String method = null;
        String path = null;
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            method = servletAttributes.getRequest().getMethod();
            path = servletAttributes.getRequest().getRequestURI();
        }
        if (errorCode.getStatus() >= 500) {
            log.error("Handled Google Calendar error code={} status={} correlationId={} method={} path={} providerCode={}",
                    errorCode.getCode(), errorCode.getStatus(), correlationId, method, path, ex.getErrorCode(), ex);
        } else {
            log.warn("Handled Google Calendar error code={} status={} correlationId={} method={} path={} providerCode={}",
                    errorCode.getCode(), errorCode.getStatus(), correlationId, method, path, ex.getErrorCode());
        }

        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(DateTimeUtil.instantNow())
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .data(null)
                .build();
        return ResponseEntity.status(errorCode.getStatus())
                .header(TraceContext.CORRELATION_ID_HEADER, correlationId)
                .header(TraceContext.TRACE_ID_HEADER, correlationId)
                .body(response);
    }

    private String applyCorrelationHeaders() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return TraceContext.ensureCurrentTraceId();
        }

        HttpServletRequest request = servletAttributes.getRequest();
        HttpServletResponse response = servletAttributes.getResponse();
        String correlationId = TraceContext.getCurrentTraceId();
        if (correlationId == null) {
            correlationId = TraceContext.resolveAndSet(
                    request.getHeader(TraceContext.CORRELATION_ID_HEADER),
                    request.getHeader(TraceContext.TRACE_ID_HEADER));
        }
        if (response != null) {
            response.setHeader(TraceContext.CORRELATION_ID_HEADER, correlationId);
            response.setHeader(TraceContext.TRACE_ID_HEADER, correlationId);
        }
        return correlationId;
    }
}
