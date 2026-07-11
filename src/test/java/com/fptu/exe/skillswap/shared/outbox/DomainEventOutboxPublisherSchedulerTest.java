package com.fptu.exe.skillswap.shared.outbox;

import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.infrastructure.realtime.DomainEventOutboxPublisherScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventOutboxPublisherSchedulerTest {

    @Mock
    private DomainEventOutboxTxHelper txHelper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RealtimeOutboxProperties properties;

    @InjectMocks
    private DomainEventOutboxPublisherScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(properties.getExchange()).thenReturn("skillswap.domain-events");
    }

    @Test
    void publishPendingEvents_shouldMarkPublishedOnSuccess() {
        DomainEventOutbox outbox = DomainEventOutbox.builder()
                .id(UUID.randomUUID())
                .aggregateType("CONVERSATION")
                .aggregateId(UUID.randomUUID())
                .eventType(DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED)
                .payloadJson("{\"messageId\":\"1\"}")
                .availableAt(LocalDateTime.now().minusMinutes(1))
                .status(DomainEventOutboxStatus.PENDING)
                .build();
        when(txHelper.reserveNextBatch(100)).thenReturn(List.of(outbox));

        boolean hadWork = scheduler.pollAndPublishPendingEvents();
        assertTrue(hadWork);

        verify(rabbitTemplate).convertAndSend(
                eq("skillswap.domain-events"),
                eq(DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED),
                eq(outbox.getPayloadJson()),
                any(MessagePostProcessor.class)
        );
        verify(txHelper).markAsPublished(outbox.getId());
    }

    @Test
    void publishPendingEvents_shouldScheduleRetryOnFailure() {
        DomainEventOutbox outbox = DomainEventOutbox.builder()
                .id(UUID.randomUUID())
                .aggregateType("NOTIFICATION")
                .aggregateId(UUID.randomUUID())
                .eventType(DomainEventOutboxEventTypes.NOTIFICATION_CREATED)
                .payloadJson("{\"notificationId\":\"1\"}")
                .availableAt(LocalDateTime.now().minusMinutes(1))
                .status(DomainEventOutboxStatus.PENDING)
                .attemptCount(0)
                .build();
        when(txHelper.reserveNextBatch(100)).thenReturn(List.of(outbox));
        doThrow(new RuntimeException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(
                        eq("skillswap.domain-events"),
                        eq(DomainEventOutboxEventTypes.NOTIFICATION_CREATED),
                        any(String.class),
                        any(MessagePostProcessor.class)
                );

        boolean hadWork = scheduler.pollAndPublishPendingEvents();
        assertTrue(hadWork);

        verify(txHelper).handlePublishFailure(outbox.getId(), 0, "broker unavailable");
    }
}
