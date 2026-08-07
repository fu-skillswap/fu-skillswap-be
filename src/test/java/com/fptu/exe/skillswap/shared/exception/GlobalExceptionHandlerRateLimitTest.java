package com.fptu.exe.skillswap.shared.exception;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerRateLimitTest {

    @Test
    void rateLimitResponseContainsRetryAfterHeaderAndBodyField() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Object>> response = handler.handleRateLimitExceeded(
                new RateLimitExceededException("Chậm lại", 35)
        );

        assertEquals(429, response.getStatusCode().value());
        assertEquals("35", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(35L, response.getBody().getRetryAfterSeconds());
        assertEquals(ErrorCode.TOO_MANY_REQUESTS.getCode(), response.getBody().getCode());
    }
}
