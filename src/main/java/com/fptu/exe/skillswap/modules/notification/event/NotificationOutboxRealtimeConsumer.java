package com.fptu.exe.skillswap.modules.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.realtime.outbox", name = "enabled", havingValue = "true")
public class NotificationOutboxRealtimeConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final RealtimeFanoutService realtimeFanoutService;

    @RabbitListener(
            queues = "${application.realtime.outbox.notification-queue:skillswap.notification.realtime}",
            containerFactory = "notificationRealtimeListenerContainerFactory"
    )
    public void consume(Message message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) throws java.io.IOException {
        restoreTraceId(message);
        try {
            String payloadJson = new String(message.getBody(), StandardCharsets.UTF_8);
            switch (routingKey) {
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.NOTIFICATION_CREATED -> handleNotificationCreated(payloadJson);
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.NOTIFICATION_BADGE_UPDATED -> handleNotificationBadgeUpdated(payloadJson);
                default -> log.debug("Ignoring unsupported notification routing key {}", routingKey);
            }
        } finally {
            TraceContext.clear();
        }
    }

    private void handleNotificationCreated(String payloadJson) throws java.io.IOException {
        Payloads.NotificationCreatedPayload payload = objectMapper.readValue(payloadJson, Payloads.NotificationCreatedPayload.class);
        NotificationResponse response = notificationService.getRealtimeNotification(payload.recipientUserId(), payload.notificationId(), payload.eventKind());
        realtimeFanoutService.pushNotificationItem(payload.recipientUserId(), response);
    }

    private void handleNotificationBadgeUpdated(String payloadJson) throws java.io.IOException {
        Payloads.NotificationBadgePayload payload = objectMapper.readValue(payloadJson, Payloads.NotificationBadgePayload.class);
        realtimeFanoutService.pushNotificationBadge(payload.recipientUserId(), payload.unreadCount(), payload.eventKind());
    }

    private static final class Payloads {
        private record NotificationCreatedPayload(UUID notificationId, UUID recipientUserId, String type, String eventKind, long unreadCount) {
        }

        private record NotificationBadgePayload(UUID recipientUserId, long unreadCount, String eventKind) {
        }
    }

    private void restoreTraceId(Message message) {
        Object traceId = message.getMessageProperties().getHeaders().get("x-trace-id");
        if (traceId instanceof String value && !value.isBlank()) {
            TraceContext.setCurrentTraceId(value);
        }
    }
}
