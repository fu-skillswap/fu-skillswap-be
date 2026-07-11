package com.fptu.exe.skillswap.shared.util;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String TRACE_ID_MDC_KEY = "traceId";

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

    public static void clear() {
        MDC.remove(REQUEST_ID_MDC_KEY);
        MDC.remove(TRACE_ID_MDC_KEY);
    }
}
