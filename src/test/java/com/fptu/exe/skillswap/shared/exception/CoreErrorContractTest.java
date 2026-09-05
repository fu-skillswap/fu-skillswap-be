package com.fptu.exe.skillswap.shared.exception;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreErrorContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearCorrelationContext() {
        com.fptu.exe.skillswap.shared.util.TraceContext.clear();
    }

    @Test
    void coreBusinessErrorsPreserveTheCommonEnvelopeAndStableCodes() {
        assertError(handler.handleBaseException(new BaseException(ErrorCode.BOOKING_ALREADY_EXISTS)),
                409, ErrorCode.BOOKING_ALREADY_EXISTS);
        assertError(handler.handleBaseException(new BaseException(ErrorCode.COURSE_ACCESS_DENIED)),
                403, ErrorCode.COURSE_ACCESS_DENIED);
        assertError(handler.handleBaseException(new BaseException(ErrorCode.NOT_FOUND)),
                404, ErrorCode.NOT_FOUND);
        assertError(handler.handleBaseException(new BaseException(ErrorCode.UNPROCESSABLE_ENTITY)),
                422, ErrorCode.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unexpectedErrorsRemainGenericServerErrors() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleAllExceptions(
                new RuntimeException("password=secret refresh_token=secret"));

        assertError(response, 500, ErrorCode.UNCATEGORIZED_EXCEPTION);
        assertEquals(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage(), response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("secret"));
        assertTrue(response.getHeaders().containsKey("X-Correlation-ID"));
    }

    @Test
    void handledBusinessErrorLogContextIsNotAddedToTheResponseEnvelope() {
        String courseId = "course-abc-123";
        ResponseEntity<ApiResponse<Object>> response = handler.handleBaseException(
                new BaseException(ErrorCode.COURSE_ACCESS_DENIED)
                        .withLogContext("courseId", courseId));

        assertNotNull(response.getHeaders().getFirst("X-Correlation-ID"));
        assertFalse(response.getBody().toString().contains(courseId));
    }

    @Test
    void unauthorizedResponseStatusExceptionRemains401() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        assertError(response, 401, ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void responseStatusExceptionKeepsKnownHttpStatusMappings() {
        assertError(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.CONFLICT)),
                409, ErrorCode.RESOURCE_CONFLICT);
        assertError(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)),
                500, ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    private void assertError(ResponseEntity<ApiResponse<Object>> response, int status, ErrorCode errorCode) {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(status, response.getBody().getStatus());
        assertEquals(errorCode.getCode(), response.getBody().getCode());
        // Error responses continue to use the existing envelope; data remains optional.
        assertEquals(errorCode.getMessage(), response.getBody().getMessage());
    }
}
