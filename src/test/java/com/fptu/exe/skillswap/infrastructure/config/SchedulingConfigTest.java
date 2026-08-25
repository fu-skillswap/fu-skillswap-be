package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.realtime.DomainEventOutboxPublisherScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class SchedulingConfigTest {

    @Test
    void emailExecutor_doesNotRunRejectedEmailWorkOnTheRequestThread() {
        @SuppressWarnings("unchecked")
        ObjectProvider<DomainEventOutboxPublisherScheduler> outboxPublisherProvider = mock(ObjectProvider.class);
        SchedulingConfig config = new SchedulingConfig(
                new RealtimeOutboxProperties(),
                outboxPublisherProvider,
                Clock.systemUTC()
        );
        TaskDecorator passThroughDecorator = runnable -> runnable;

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.emailTaskExecutor(passThroughDecorator);

        assertFalse(executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                        instanceof ThreadPoolExecutor.CallerRunsPolicy,
                "Email overload must not run SMTP work on the booking/payment request thread");
        executor.shutdown();
    }
}
