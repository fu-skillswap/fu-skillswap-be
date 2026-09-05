package com.fptu.exe.skillswap.shared.exception;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
public class BaseException extends RuntimeException {
    private static final Set<String> SAFE_LOG_CONTEXT_KEYS = Set.of(
            "bookingId",
            "courseId",
            "paymentOrderId",
            "paymentAttemptId",
            "providerOrderCode"
    );

    private final ErrorCode errorCode;
    private final Map<String, String> logContext = new LinkedHashMap<>();

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Adds an allow-listed identifier for backend diagnostics without exposing it
     * in the API response. Values are deliberately restricted to identifiers and
     * never include request payloads, credentials, or exception messages.
     */
    public BaseException withLogContext(String key, Object value) {
        if (SAFE_LOG_CONTEXT_KEYS.contains(key) && value != null) {
            String rendered = value instanceof UUID || value instanceof Number
                    ? value.toString()
                    : value.toString().replaceAll("[^a-zA-Z0-9\\-_.]", "");
            if (!rendered.isBlank() && rendered.length() <= 128) {
                logContext.put(key, rendered);
            }
        }
        return this;
    }
}

