package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;
import com.fptu.exe.skillswap.modules.chat.service.GroupChatFanoutDispatcher;
import com.fptu.exe.skillswap.modules.chat.service.GroupChatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupChatFanoutDispatcherTest {

    @Mock
    private RealtimeFanoutService realtimeFanoutService;

    @Mock
    private ChatRealtimeDeliveryDeduplicationService deduplicationService;

    private GroupChatProperties properties;
    private GroupChatFanoutDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new GroupChatProperties();
        properties.setBatchSize(25);
        properties.setBatchDelayMs(50L);

        dispatcher = new GroupChatFanoutDispatcher(realtimeFanoutService, properties, deduplicationService);
        when(deduplicationService.claim(any(), any())).thenReturn(true);
    }

    @Test
    void testFanoutWithSmallGroup() {
        UUID conversationId = UUID.randomUUID();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(UUID.randomUUID())
                .sequence(1L)
                .conversationType(ConversationType.GROUP)
                .createdAt(Instant.parse("2026-06-24T04:45:00Z"))
                .build();

        List<UUID> recipientIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        dispatcher.dispatchGroupMessage(conversationId, event, recipientIds);

        verify(realtimeFanoutService, times(3)).pushChatMessage(any(), eq(event));
    }

    @Test
    void testFanoutWithLargeGroupOverBatchSize() {
        UUID conversationId = UUID.randomUUID();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(UUID.randomUUID())
                .sequence(1L)
                .conversationType(ConversationType.GROUP)
                .createdAt(Instant.parse("2026-06-24T04:45:00Z"))
                .build();

        // 60 recipients with batchSize = 25 -> three synchronous chunks.
        List<UUID> recipientIds = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            recipientIds.add(UUID.randomUUID());
        }

        dispatcher.dispatchGroupMessage(conversationId, event, recipientIds);

        verify(realtimeFanoutService, times(60)).pushChatMessage(any(), eq(event));
    }

    @Test
    void partialFailure_shouldRetryOnlyUndeliveredRecipients() {
        UUID conversationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(UUID.randomUUID())
                .sequence(1L)
                .conversationType(ConversationType.GROUP)
                .createdAt(Instant.parse("2026-06-24T04:45:00Z"))
                .build();
        UUID firstRecipient = UUID.randomUUID();
        UUID secondRecipient = UUID.randomUUID();

        when(deduplicationService.claim(eventId, firstRecipient)).thenReturn(true, false);
        when(deduplicationService.claim(eventId, secondRecipient)).thenReturn(true, true);
        boolean[] failedOnce = {false};
        doAnswer(invocation -> {
            UUID recipientId = invocation.getArgument(0);
            if (recipientId.equals(secondRecipient) && !failedOnce[0]) {
                failedOnce[0] = true;
                throw new IllegalStateException("temporary broker failure");
            }
            return null;
        }).when(realtimeFanoutService).pushChatMessage(any(), eq(event));

        assertThrows(IllegalStateException.class,
                () -> dispatcher.dispatchGroupMessage(eventId, conversationId, event,
                        List.of(firstRecipient, secondRecipient)));
        dispatcher.dispatchGroupMessage(eventId, conversationId, event,
                List.of(firstRecipient, secondRecipient));

        verify(realtimeFanoutService, times(1)).pushChatMessage(firstRecipient, event);
        verify(realtimeFanoutService, times(2)).pushChatMessage(secondRecipient, event);
        verify(deduplicationService, times(1)).markDelivered(eventId, firstRecipient);
    }
}
