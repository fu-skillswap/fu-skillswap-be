package com.fptu.exe.skillswap.modules.conversation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
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

    @RabbitListener(
            queues = "${application.realtime.outbox.chat-queue:skillswap.chat.realtime}",
            concurrency = "1",
            containerFactory = "chatRealtimeListenerContainerFactory"
    )
    public void consume(Message message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) throws java.io.IOException {
        restoreTraceId(message);
        try {
            String payloadJson = new String(message.getBody(), StandardCharsets.UTF_8);
            switch (routingKey) {
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED -> handleChatMessageCreated(payloadJson);
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.CHAT_CONVERSATION_UPDATED -> handleConversationUpdated(payloadJson);
                case com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.CHAT_UNREAD_COUNT_UPDATED -> handleUnreadUpdated(payloadJson);
                default -> log.debug("Ignoring unsupported chat routing key {}", routingKey);
            }
        } finally {
            TraceContext.clear();
        }
    }

    private void handleChatMessageCreated(String payloadJson) throws java.io.IOException {
        Payloads.ChatMessageCreatedPayload payload = objectMapper.readValue(payloadJson, Payloads.ChatMessageCreatedPayload.class);
        conversationService.buildChatMessageDeliveries(payload.conversationId(), payload.messageId(), payload.senderId())
                .forEach(delivery -> realtimeFanoutService.pushChatMessage(delivery.recipientUserId(), delivery.event()));
    }

    private void handleConversationUpdated(String payloadJson) throws java.io.IOException {
        Payloads.ChatConversationUpdatedPayload payload = objectMapper.readValue(payloadJson, Payloads.ChatConversationUpdatedPayload.class);
        for (UUID participantUserId : conversationService.getConversationParticipantUserIds(payload.conversationId())) {
            ConversationResponse response = conversationService.getConversationDetail(payload.conversationId(), participantUserId);
            realtimeFanoutService.pushConversationSummary(participantUserId, response);
        }
    }

    private void handleUnreadUpdated(String payloadJson) throws java.io.IOException {
        Payloads.ChatUnreadCountUpdatedPayload payload = objectMapper.readValue(payloadJson, Payloads.ChatUnreadCountUpdatedPayload.class);
        long totalUnreadCount = conversationService.getTotalUnreadCount(payload.recipientUserId());
        realtimeFanoutService.pushChatUnread(payload.recipientUserId(), totalUnreadCount);
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
}
