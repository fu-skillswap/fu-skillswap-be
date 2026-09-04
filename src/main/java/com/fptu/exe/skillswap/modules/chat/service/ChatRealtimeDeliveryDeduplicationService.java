package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.ChatRealtimeDeliveryRecord;
import com.fptu.exe.skillswap.modules.chat.domain.ChatRealtimeDeliveryStatus;
import com.fptu.exe.skillswap.modules.chat.repository.ChatRealtimeDeliveryRecordRepository;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Durable per-recipient idempotency for Rabbit-backed Chat realtime events. */
@Service
@RequiredArgsConstructor
public class ChatRealtimeDeliveryDeduplicationService {

    private final ChatRealtimeDeliveryRecordRepository repository;

    @Transactional
    public boolean claim(UUID outboxEventId, UUID recipientUserId) {
        if (outboxEventId == null || recipientUserId == null) {
            return true;
        }
        repository.insertIfAbsent(UUID.randomUUID(), outboxEventId, recipientUserId, DateTimeUtil.now());
        return repository.findByOutboxEventIdAndRecipientUserId(outboxEventId, recipientUserId)
                .map(record -> record.getStatus() != ChatRealtimeDeliveryStatus.DELIVERED)
                .orElse(false);
    }

    @Transactional
    public void markDelivered(UUID outboxEventId, UUID recipientUserId) {
        if (outboxEventId == null || recipientUserId == null) {
            return;
        }
        repository.updateStatus(outboxEventId, recipientUserId,
                ChatRealtimeDeliveryStatus.DELIVERED, DateTimeUtil.now());
    }
}
