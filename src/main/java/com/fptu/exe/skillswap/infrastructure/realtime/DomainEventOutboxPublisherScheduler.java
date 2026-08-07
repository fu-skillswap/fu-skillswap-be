package com.fptu.exe.skillswap.infrastructure.realtime;

import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutbox;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxTxHelper;
import com.fptu.exe.skillswap.shared.outbox.OutboxWakeupEvent;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.realtime.outbox", name = "enabled", havingValue = "true")
public class DomainEventOutboxPublisherScheduler {

    private final DomainEventOutboxTxHelper txHelper;
    private final RabbitTemplate rabbitTemplate;
    private final RealtimeOutboxProperties properties;

    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    @org.springframework.scheduling.annotation.Async("applicationTaskScheduler")
    public void onOutboxWakeup(OutboxWakeupEvent event) {
        try {
            pollAndPublishPendingEvents();
        } catch (Exception ex) {
            log.error("Error during outbox wakeup polling", ex);
        }
    }

    public boolean pollAndPublishPendingEvents() {
        List<DomainEventOutbox> batch = txHelper.reserveNextBatch(100);
        if (batch.isEmpty()) {
            return false;
        }
        for (DomainEventOutbox outbox : batch) {
            try {
                TraceContext.setCurrentTraceId(outbox.getTraceId());
                publishWithBrokerConfirm(outbox);
                txHelper.markAsPublished(outbox.getId());
            } catch (RuntimeException ex) {
                txHelper.handlePublishFailure(outbox.getId(), outbox.getAttemptCount(), ex.getMessage());
                log.warn("Failed to publish outbox event id={} type={}: {}", outbox.getId(), outbox.getEventType(), ex.getMessage());
            } finally {
                TraceContext.clear();
            }
        }
        return true;
    }

    private void publishWithBrokerConfirm(DomainEventOutbox outbox) {
        CorrelationData correlationData = new CorrelationData(outbox.getId().toString());
        rabbitTemplate.convertAndSend(properties.getExchange(), outbox.getEventType(), outbox.getPayloadJson(), message -> {
                    message.getMessageProperties().setContentType("application/json");
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(outbox.getId() == null ? null : outbox.getId().toString());
                    message.getMessageProperties().setHeader("x-outbox-id", outbox.getId() == null ? null : outbox.getId().toString());
                    message.getMessageProperties().setHeader("x-event-type", outbox.getEventType());
                    message.getMessageProperties().setHeader("x-aggregate-type", outbox.getAggregateType());
                    message.getMessageProperties().setHeader("x-aggregate-id", outbox.getAggregateId() == null ? null : outbox.getAggregateId().toString());
                    message.getMessageProperties().setHeader("x-outbox-attempt-count", outbox.getAttemptCount() + 1);
                    message.getMessageProperties().setHeader("x-trace-id", outbox.getTraceId());
                    return message;
                }, correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(properties.getPublisherConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("Rabbit broker rejected event: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("Rabbit returned an unroutable realtime event");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Rabbit publisher confirm", ex);
        } catch (java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("Timed out waiting for Rabbit publisher confirm", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IllegalStateException("Rabbit publisher confirm failed", ex.getCause());
        }
    }
}
