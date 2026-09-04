package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class GroupChatFanoutDispatcher {

    private final RealtimeFanoutService realtimeFanoutService;
    private final GroupChatProperties properties;
    private final ChatRealtimeDeliveryDeduplicationService deduplicationService;

    public GroupChatFanoutDispatcher(RealtimeFanoutService realtimeFanoutService,
                                     GroupChatProperties properties,
                                     ChatRealtimeDeliveryDeduplicationService deduplicationService) {
        this.realtimeFanoutService = realtimeFanoutService;
        this.properties = properties;
        this.deduplicationService = deduplicationService;
    }

    public void dispatchGroupMessage(UUID conversationId,
                                     ChatMessageEvent event,
                                     List<UUID> recipientUserIds) {
        dispatchGroupMessage(null, conversationId, event, recipientUserIds);
    }

    /**
     * Delivers all chunks before returning so the Rabbit listener can acknowledge
     * only after the durable event has been fully handled.
     */
    public void dispatchGroupMessage(UUID outboxEventId,
                                     UUID conversationId,
                                     ChatMessageEvent event,
                                     List<UUID> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        int batchSize = Math.max(1, properties.getBatchSize());
        List<List<UUID>> chunks = partition(recipientUserIds, batchSize);
        log.debug("Dispatching group message id={} in conversationId={} to {} recipients across {} chunks (batchSize={})",
                event.messageId(), conversationId, recipientUserIds.size(), chunks.size(), batchSize);

        for (List<UUID> chunk : chunks) {
            processChunk(outboxEventId, conversationId, event, chunk);
        }
    }

    private void processChunk(UUID outboxEventId,
                              UUID conversationId,
                              ChatMessageEvent event,
                              List<UUID> chunkRecipientIds) {
        for (UUID recipientId : chunkRecipientIds) {
            if (!deduplicationService.claim(outboxEventId, recipientId)) {
                continue;
            }
            try {
                realtimeFanoutService.pushChatMessage(recipientId, event);
                deduplicationService.markDelivered(outboxEventId, recipientId);
            } catch (Exception ex) {
                log.warn("Failed to push group chat message to recipientId={} in conversationId={}: {}",
                        recipientId, conversationId, ex.getMessage());
                throw ex;
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
