package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxPublisherScheduler;
import com.fptu.exe.skillswap.shared.outbox.DynamicPeriodicTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class SchedulingConfig implements SchedulingConfigurer {

    private final RealtimeOutboxProperties realtimeOutboxProperties;
    private final ObjectProvider<DomainEventOutboxPublisherScheduler> outboxPublisherSchedulerProvider;

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    @Bean(name = "applicationTaskScheduler")
    public ThreadPoolTaskScheduler applicationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setPoolSize(5);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "slotGenerationExecutor")
    public TaskExecutor slotGenerationExecutor(TaskDecorator mdcTaskDecorator) {
        return buildExecutor("slot-gen-", 2, 4, 20, new ThreadPoolExecutor.CallerRunsPolicy(), mdcTaskDecorator);
    }

    @Bean(name = "mailNotificationExecutor")
    public TaskExecutor mailNotificationExecutor(TaskDecorator mdcTaskDecorator) {
        return buildExecutor("mail-noti-", 2, 4, 100, new ThreadPoolExecutor.DiscardPolicy(), mdcTaskDecorator);
    }

    @Bean(name = "notificationExecutor")
    public TaskExecutor notificationExecutor(TaskDecorator mdcTaskDecorator) {
        return buildExecutor("app-noti-", 4, 8, 1000, new ThreadPoolExecutor.CallerRunsPolicy(), mdcTaskDecorator);
    }

    @Bean(name = "emailTaskExecutor")
    public TaskExecutor emailTaskExecutor(TaskDecorator mdcTaskDecorator) {
        return buildExecutor("email-", 2, 5, 500, new ThreadPoolExecutor.CallerRunsPolicy(), mdcTaskDecorator);
    }

    @Bean(name = "forumTaskExecutor")
    public TaskExecutor forumTaskExecutor(TaskDecorator mdcTaskDecorator) {
        return buildExecutor("forum-", 2, 10, 100, new ThreadPoolExecutor.CallerRunsPolicy(), mdcTaskDecorator);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        TaskScheduler scheduler = applicationTaskScheduler();
        taskRegistrar.setTaskScheduler(scheduler);

        if (!realtimeOutboxProperties.isEnabled()) {
            return;
        }

        DomainEventOutboxPublisherScheduler outboxScheduler = outboxPublisherSchedulerProvider.getIfAvailable();
        if (outboxScheduler == null) {
            log.warn("Realtime outbox is enabled but publisher scheduler bean is unavailable");
            return;
        }

        DynamicPeriodicTrigger trigger = new DynamicPeriodicTrigger(
                realtimeOutboxProperties.getPollMs(),
                realtimeOutboxProperties.getPollMs() * 20L
        );
        taskRegistrar.addTriggerTask(() -> {
            boolean hadWork = false;
            try {
                hadWork = outboxScheduler.pollAndPublishPendingEvents();
            } catch (Exception ex) {
                log.error("Realtime outbox polling task failed", ex);
            } finally {
                trigger.recordPollResult(hadWork);
            }
        }, trigger);
    }

    private TaskExecutor buildExecutor(String prefix,
                                       int corePoolSize,
                                       int maxPoolSize,
                                       int queueCapacity,
                                       java.util.concurrent.RejectedExecutionHandler rejectedExecutionHandler,
                                       TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        return executor;
    }
}

