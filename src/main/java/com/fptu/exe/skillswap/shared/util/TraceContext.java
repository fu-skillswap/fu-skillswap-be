package com.fptu.exe.skillswap.shared.util;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class TraceContext {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final int MAX_ID_LENGTH = 64;

    private TraceContext() {
    }

    public static void setCurrentTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            clear();
            return;
        }
        MDC.put(REQUEST_ID_MDC_KEY, traceId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
    }

    public static String getCurrentTraceId() {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return MDC.get(REQUEST_ID_MDC_KEY);
    }

    /**
     * Resolves the public correlation header while retaining compatibility with
     * the existing X-Request-Id header.
     */
    public static String resolveAndSet(String correlationHeader, String legacyTraceHeader) {
        String candidate = StringUtils.hasText(correlationHeader) ? correlationHeader : legacyTraceHeader;
        String resolved = sanitize(candidate);
        if (!StringUtils.hasText(resolved)) {
            resolved = UUID.randomUUID().toString();
        }
        setCurrentTraceId(resolved);
        return resolved;
    }

    public static String ensureCurrentTraceId() {
        String current = getCurrentTraceId();
        return StringUtils.hasText(current) ? current : resolveAndSet(null, null);
    }

    private static String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9\\-_.]", "");
        if (!StringUtils.hasText(sanitized)) {
            return null;
        }
        return sanitized.length() > MAX_ID_LENGTH ? sanitized.substring(0, MAX_ID_LENGTH) : sanitized;
    }

    public static void clear() {
        MDC.remove(REQUEST_ID_MDC_KEY);
        MDC.remove(TRACE_ID_MDC_KEY);
    }
}
