package com.fptu.exe.skillswap.modules.chat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.service.ChatRealtimeDeliveryDeduplicationService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
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
public class ChatOutboxRealtimeConsumer {

    private final ObjectMapper objectMapper;
    private final ConversationService conversationService;
    private final RealtimeFanoutService realtimeFanoutService;
    private final org.springframework.beans.factory.ObjectProvider<com.fptu.exe.skillswap.modules.chat.service.GroupChatFanoutDispatcher> groupChatFanoutDispatcherProvider;
    private final ChatRealtimeDeliveryDeduplicationService deduplicationService;

    @RabbitListener(
            queues = "${application.realtime.outbox.chat-queue:skillswap.chat.realtime}",
            concurrency = "1",
            containerFactory = "chatRealtimeListenerContainerFactory"
    )
    public void consume(Message message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) throws java.io.IOException {
        restoreTraceId(message);
        UUID outboxEventId = null;
        int retryCount = headerInt(message, "x-outbox-attempt-count");
        try {
            outboxEventId = resolveOutboxEventId(message);
            String payloadJson = new String(message.getBody(), StandardCharsets.UTF_8);
            switch (routingKey) {
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED -> handleChatMessageCreated(payloadJson, outboxEventId);
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.CHAT_CONVERSATION_UPDATED -> handleConversationUpdated(payloadJson, outboxEventId);
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.CHAT_UNREAD_COUNT_UPDATED -> handleUnreadUpdated(payloadJson, outboxEventId);
                default -> log.debug("Ignoring unsupported chat routing key {}", routingKey);
            }
        } catch (java.io.IOException exception) {
            log.error("Chat realtime consumer failed eventId={} aggregateId={} retryCount={} reason={}",
                    outboxEventId, header(message, "x-aggregate-id"), retryCount, exception.getMessage(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Chat realtime consumer failed eventId={} aggregateId={} retryCount={} reason={}",
                    outboxEventId, header(message, "x-aggregate-id"), retryCount, exception.getMessage(), exception);
            throw exception;
        } finally {
            TraceContext.clear();
        }
    }

    private void handleChatMessageCreated(String payloadJson, UUID outboxEventId) throws java.io.IOException {
        Payloads.ChatMessageCreatedPayload payload = objectMapper.readValue(payloadJson, Payloads.ChatMessageCreatedPayload.class);
        var conversation = conversationService.findById(payload.conversationId());
        if (conversation != null && conversation.getType() == com.fptu.exe.skillswap.modules.chat.domain.ConversationType.GROUP) {
            var recipientIds = conversationService.getActiveRecipientUserIds(payload.conversationId(), payload.senderId());
            var event = conversationService.buildGroupChatMessageEvent(payload.conversationId(), payload.messageId(), payload.senderId());
            var dispatcher = groupChatFanoutDispatcherProvider.getIfAvailable();
            if (dispatcher != null) {
                dispatcher.dispatchGroupMessage(outboxEventId, payload.conversationId(), event, recipientIds);
            } else {
                recipientIds.forEach(recipientId -> deliverOnce(outboxEventId, recipientId,
                        () -> realtimeFanoutService.pushChatMessage(recipientId, event)));
            }
        } else {
            conversationService.buildChatMessageDeliveries(payload.conversationId(), payload.messageId(), payload.senderId())
                    .forEach(delivery -> deliverOnce(outboxEventId, delivery.recipientUserId(),
                            () -> realtimeFanoutService.pushChatMessage(delivery.recipientUserId(), delivery.event())));
        }
    }

    private void handleConversationUpdated(String payloadJson, UUID outboxEventId) throws java.io.IOException {
        Payloads.ChatConversationUpdatedPayload payload = objectMapper.readValue(payloadJson, Payloads.ChatConversationUpdatedPayload.class);
        for (UUID participantUserId : conversationService.getConversationParticipantUserIds(payload.conversationId())) {
            ConversationResponse response = conversationService.getConversationDetail(payload.conversationId(), participantUserId);
            deliverOnce(outboxEventId, participantUserId,
                    () -> realtimeFanoutService.pushConversationSummary(participantUserId, response));
        }
    }

    private void handleUnreadUpdated(String payloadJson, UUID outboxEventId) throws java.io.IOException {
        Payloads.ChatUnreadCountUpdatedPayload payload = objectMapper.readValue(payloadJson, Payloads.ChatUnreadCountUpdatedPayload.class);
        if (deduplicationService.claim(outboxEventId, payload.recipientUserId())) {
            long totalUnreadCount = conversationService.getTotalUnreadCount(payload.recipientUserId());
            realtimeFanoutService.pushChatUnread(payload.recipientUserId(), totalUnreadCount);
            deduplicationService.markDelivered(outboxEventId, payload.recipientUserId());
        }
    }

    private static final class Payloads {
        private record ChatMessageCreatedPayload(UUID conversationId, UUID messageId, UUID senderId) {
        }

        private record ChatConversationUpdatedPayload(UUID conversationId, UUID actorUserId) {
        }

        private record ChatUnreadCountUpdatedPayload(UUID conversationId, UUID recipientUserId) {
        }
    }

    private void restoreTraceId(Message message) {
        Object traceId = message.getMessageProperties().getHeaders().get("x-trace-id");
        if (traceId instanceof String value && !value.isBlank()) {
            TraceContext.setCurrentTraceId(value);
        }
    }

    private void deliverOnce(UUID outboxEventId, UUID recipientUserId, Runnable delivery) {
        if (!deduplicationService.claim(outboxEventId, recipientUserId)) {
            return;
        }
        try {
            delivery.run();
            deduplicationService.markDelivered(outboxEventId, recipientUserId);
        } catch (RuntimeException exception) {
            log.warn("Realtime delivery failed for eventId={} recipientId={}: {}",
                    outboxEventId, recipientUserId, exception.getMessage());
            throw exception;
        }
    }

    private UUID resolveOutboxEventId(Message message) {
        Object raw = message.getMessageProperties().getHeaders().get("x-outbox-id");
        if (raw == null) {
            raw = message.getMessageProperties().getMessageId();
        }
        if (raw == null) {
            throw new IllegalArgumentException("Realtime message is missing durable outbox event id");
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Realtime message has malformed durable outbox event id", exception);
        }
    }

    private int headerInt(Message message, String key) {
        Object raw = header(message, key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return raw == null ? 0 : Integer.parseInt(raw.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Object header(Message message, String key) {
        return message.getMessageProperties().getHeaders().get(key);
    }
}
