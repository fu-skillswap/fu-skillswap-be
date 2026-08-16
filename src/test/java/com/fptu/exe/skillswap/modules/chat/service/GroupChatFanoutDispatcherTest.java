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
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GroupChatFanoutDispatcherTest {

    @Mock
    private RealtimeFanoutService realtimeFanoutService;

    @Mock
    private TaskScheduler taskScheduler;

    private GroupChatProperties properties;
    private GroupChatFanoutDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new GroupChatProperties();
        properties.setBatchSize(25);
        properties.setBatchDelayMs(50L);

        dispatcher = new GroupChatFanoutDispatcher(realtimeFanoutService, properties, taskScheduler);
    }

    @Test
    void testFanoutWithSmallGroup() {
        UUID conversationId = UUID.randomUUID();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(UUID.randomUUID())
                .sequence(1L)
                .conversationType(ConversationType.GROUP)
                .createdAt(LocalDateTime.now())
                .build();

        List<UUID> recipientIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        dispatcher.dispatchGroupMessage(conversationId, event, recipientIds);

        verify(realtimeFanoutService, times(3)).pushChatMessage(any(), eq(event));
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void testFanoutWithLargeGroupOverBatchSize() {
        UUID conversationId = UUID.randomUUID();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(UUID.randomUUID())
                .sequence(1L)
                .conversationType(ConversationType.GROUP)
                .createdAt(LocalDateTime.now())
                .build();

        // 60 recipients with batchSize = 25 -> Chunk 1 (25, immediate), Chunk 2 (25, scheduled), Chunk 3 (10, scheduled)
        List<UUID> recipientIds = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            recipientIds.add(UUID.randomUUID());
        }

        dispatcher.dispatchGroupMessage(conversationId, event, recipientIds);

        // First 25 pushed immediately
        verify(realtimeFanoutService, times(25)).pushChatMessage(any(), eq(event));
        // Next 2 chunks scheduled via taskScheduler
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }
}
