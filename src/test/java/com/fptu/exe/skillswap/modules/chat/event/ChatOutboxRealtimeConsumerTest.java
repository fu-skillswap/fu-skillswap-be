package com.fptu.exe.skillswap.modules.chat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;
import com.fptu.exe.skillswap.modules.chat.service.ChatRealtimeDeliveryDeduplicationService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.chat.service.GroupChatFanoutDispatcher;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOutboxRealtimeConsumerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private RealtimeFanoutService realtimeFanoutService;

    @Mock
    private GroupChatFanoutDispatcher groupChatFanoutDispatcher;

    @Mock
    private ObjectProvider<GroupChatFanoutDispatcher> groupChatFanoutDispatcherProvider;

    @Mock
    private ChatRealtimeDeliveryDeduplicationService deduplicationService;

    @Test
    void groupMessageOutboxEvent_shouldDispatchExactlyOnce() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.GROUP)
                .build();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .senderId(senderId)
                .conversationType(ConversationType.GROUP)
                .build();

        when(conversationService.findById(conversationId)).thenReturn(conversation);
        when(conversationService.getActiveRecipientUserIds(conversationId, senderId)).thenReturn(List.of(recipientId));
        when(conversationService.buildGroupChatMessageEvent(conversationId, messageId, senderId)).thenReturn(event);
        when(groupChatFanoutDispatcherProvider.getIfAvailable()).thenReturn(groupChatFanoutDispatcher);

        ChatOutboxRealtimeConsumer consumer = new ChatOutboxRealtimeConsumer(
                new ObjectMapper(),
                conversationService,
                realtimeFanoutService,
                groupChatFanoutDispatcherProvider,
                deduplicationService
        );
        String payload = "{\"conversationId\":\"" + conversationId + "\",\"messageId\":\"" + messageId + "\",\"senderId\":\"" + senderId + "\"}";

        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8));
        message.getMessageProperties().setHeader("x-outbox-id", UUID.randomUUID().toString());
        consumer.consume(message, DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED);

        verify(groupChatFanoutDispatcher, times(1)).dispatchGroupMessage(any(), eq(conversationId), eq(event), eq(List.of(recipientId)));
        verify(realtimeFanoutService, never()).pushChatMessage(any(), any());
    }

    @Test
    void duplicateDirectMessageEvent_shouldDeliverOnce() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.DIRECT)
                .build();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .senderId(senderId)
                .conversationType(ConversationType.DIRECT)
                .build();

        when(conversationService.findById(conversationId)).thenReturn(conversation);
        when(conversationService.buildChatMessageDeliveries(conversationId, messageId, senderId))
                .thenReturn(List.of(new com.fptu.exe.skillswap.modules.chat.event.ChatMessageRealtimeDelivery(recipientId, event)));
        when(deduplicationService.claim(eventId, recipientId)).thenReturn(true, false);

        ChatOutboxRealtimeConsumer consumer = new ChatOutboxRealtimeConsumer(
                new ObjectMapper(),
                conversationService,
                realtimeFanoutService,
                groupChatFanoutDispatcherProvider,
                deduplicationService
        );
        String payload = "{\"conversationId\":\"" + conversationId + "\",\"messageId\":\"" + messageId
                + "\",\"senderId\":\"" + senderId + "\"}";
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8));
        message.getMessageProperties().setHeader("x-outbox-id", eventId.toString());

        consumer.consume(message, DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED);
        consumer.consume(message, DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED);

        verify(realtimeFanoutService, times(1)).pushChatMessage(recipientId, event);
        verify(deduplicationService, times(1)).markDelivered(eventId, recipientId);
    }

    @Test
    void consumerRetryAfterPartialDelivery_shouldReplayStableMessageIdForClientDeduplication() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Conversation conversation = Conversation.builder().id(conversationId).type(ConversationType.DIRECT).build();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId).messageId(messageId).sequence(42L).senderId(senderId)
                .conversationType(ConversationType.DIRECT).build();
        when(conversationService.findById(conversationId)).thenReturn(conversation);
        when(conversationService.buildChatMessageDeliveries(conversationId, messageId, senderId))
                .thenReturn(List.of(new com.fptu.exe.skillswap.modules.chat.event.ChatMessageRealtimeDelivery(recipientId, event)));
        when(deduplicationService.claim(eventId, recipientId)).thenReturn(true, true);
        doThrow(new RuntimeException("socket closed"))
                .doNothing()
                .when(realtimeFanoutService).pushChatMessage(recipientId, event);

        ChatOutboxRealtimeConsumer consumer = new ChatOutboxRealtimeConsumer(
                new ObjectMapper(), conversationService, realtimeFanoutService,
                groupChatFanoutDispatcherProvider, deduplicationService);
        String payload = "{\"conversationId\":\"" + conversationId + "\",\"messageId\":\"" + messageId
                + "\",\"senderId\":\"" + senderId + "\"}";
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8));
        message.getMessageProperties().setHeader("x-outbox-id", eventId.toString());
        message.getMessageProperties().setHeader("x-outbox-attempt-count", 2);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.consume(message, DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED));
        consumer.consume(message, DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED);

        verify(realtimeFanoutService, times(2)).pushChatMessage(recipientId, event);
        verify(deduplicationService, times(1)).markDelivered(eventId, recipientId);
    }
}
