package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.util.ErrorHandler;

import java.lang.reflect.Method;

/** Logs uncaught void @Async failures with MDC trace and invocation metadata. */
@Slf4j
public class AsyncExceptionTraceabilityHandler implements AsyncUncaughtExceptionHandler, ErrorHandler {

    @Override
    public void handleUncaughtException(Throwable exception, Method method, Object... params) {
        String correlationId = TraceContext.getCurrentTraceId();
        String className = method == null ? "unknown" : method.getDeclaringClass().getName();
        String methodName = method == null ? "unknown" : method.getName();

        if (exception instanceof BaseException businessException) {
            log.warn("Async business failure correlationId={} class={} method={} exceptionType={} errorCode={} safeMessage={}",
                    correlationId, className, methodName, exception.getClass().getSimpleName(),
                    businessException.getErrorCode().getCode(), businessException.getErrorCode().getMessage());
            return;
        }

        log.error("Async system failure correlationId={} class={} method={} exceptionType={} safeMessage={}",
                correlationId, className, methodName, exception.getClass().getSimpleName(), safeMessage(exception), exception);
    }

    @Override
    public void handleError(Throwable exception) {
        log.error("Scheduled system failure correlationId={} thread={} exceptionType={} safeMessage={}",
                TraceContext.getCurrentTraceId(), Thread.currentThread().getName(),
                exception.getClass().getSimpleName(), safeMessage(exception), exception);
    }

    private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "No diagnostic message";
        }
        return message.length() <= 256 ? message : message.substring(0, 256) + "…";
    }
}
