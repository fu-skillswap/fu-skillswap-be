package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupChatFanoutDispatcher {

    private final RealtimeFanoutService realtimeFanoutService;
    private final GroupChatProperties properties;
    @Qualifier("applicationTaskScheduler")
    private final TaskScheduler taskScheduler;

    public void dispatchGroupMessage(UUID conversationId,
                                     ChatMessageEvent event,
                                     List<UUID> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        int batchSize = Math.max(1, properties.getBatchSize());
        long delayMs = Math.max(0, properties.getBatchDelayMs());

        List<List<UUID>> chunks = partition(recipientUserIds, batchSize);
        log.debug("Dispatching group message id={} in conversationId={} to {} recipients across {} chunks (batchSize={}, delayMs={})",
                event.messageId(), conversationId, recipientUserIds.size(), chunks.size(), batchSize, delayMs);

        for (int i = 0; i < chunks.size(); i++) {
            List<UUID> chunk = chunks.get(i);
            long executionDelay = i * delayMs;

            if (executionDelay == 0) {
                processChunk(conversationId, event, chunk);
            } else {
                taskScheduler.schedule(() -> processChunk(conversationId, event, chunk),
                        Instant.now().plusMillis(executionDelay));
            }
        }
    }

    private void processChunk(UUID conversationId, ChatMessageEvent event, List<UUID> chunkRecipientIds) {
        for (UUID recipientId : chunkRecipientIds) {
            try {
                realtimeFanoutService.pushChatMessage(recipientId, event);
            } catch (Exception ex) {
                log.warn("Failed to push group chat message to recipientId={} in conversationId={}: {}",
                        recipientId, conversationId, ex.getMessage());
            }
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
