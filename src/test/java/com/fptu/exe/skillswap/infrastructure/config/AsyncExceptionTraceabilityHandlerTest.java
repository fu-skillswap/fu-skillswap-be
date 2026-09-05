package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AsyncExceptionTraceabilityHandlerTest {

    private final AsyncExceptionTraceabilityHandler handler = new AsyncExceptionTraceabilityHandler();

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void shouldProvideUnifiedHandlerFromSchedulingConfig() {
        assertNotNull(handler);
        assertDoesNotThrow(() -> handler.handleUncaughtException(
                new BaseException(ErrorCode.CHAT_ACCESS_DENIED),
                SampleAsyncTarget.class.getDeclaredMethod("sendNotification")));
    }

    @Test
    void shouldTraceUnexpectedAsyncFailureWithoutChangingCallerFlow() throws NoSuchMethodException {
        Method method = SampleAsyncTarget.class.getDeclaredMethod("sendNotification");
        TraceContext.setCurrentTraceId("async-correlation-1");

        assertDoesNotThrow(() -> handler.handleUncaughtException(
                new RuntimeException("provider timeout"), method));
    }

    @Test
    void shouldTraceUncaughtScheduledFailure() {
        assertDoesNotThrow(() -> handler.handleError(new RuntimeException("scheduled provider timeout")));
    }

    private static final class SampleAsyncTarget {
        @SuppressWarnings("unused")
        private void sendNotification() {
        }
    }
}
