package com.fptu.exe.skillswap.shared.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        TraceContext.clear();
    }

    @Test
    void handledErrorLogContainsCorrelationAndSafeBusinessContext() {
        appender.start();
        logger.addAppender(appender);
        TraceContext.setCurrentTraceId("log-correlation-123");

        new GlobalExceptionHandler().handleBaseException(
                new BaseException(ErrorCode.BOOKING_EXPIRED)
                        .withLogContext("bookingId", "booking-123"));

        assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("code=BOOKING_4004")
                        && message.contains("status=409")
                        && message.contains("correlationId=log-correlation-123")
                        && message.contains("bookingId=booking-123")));
    }
}
