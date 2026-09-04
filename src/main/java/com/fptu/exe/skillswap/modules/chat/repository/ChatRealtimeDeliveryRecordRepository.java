package com.fptu.exe.skillswap.modules.chat.repository;

import com.fptu.exe.skillswap.modules.chat.domain.ChatRealtimeDeliveryRecord;
import com.fptu.exe.skillswap.modules.chat.domain.ChatRealtimeDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ChatRealtimeDeliveryRecordRepository extends JpaRepository<ChatRealtimeDeliveryRecord, UUID> {

    Optional<ChatRealtimeDeliveryRecord> findByOutboxEventIdAndRecipientUserId(UUID outboxEventId, UUID recipientUserId);

    @Modifying
    @Query(value = """
            INSERT INTO chat_realtime_delivery_records
                (id, outbox_event_id, recipient_user_id, status, created_at)
            VALUES (:id, :eventId, :recipientId, 'PENDING', :createdAt)
            ON CONFLICT (outbox_event_id, recipient_user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("eventId") UUID eventId,
                       @Param("recipientId") UUID recipientId,
                       @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Query("""
            update ChatRealtimeDeliveryRecord record
               set record.status = :status,
                   record.deliveredAt = :deliveredAt
             where record.outboxEventId = :eventId
               and record.recipientUserId = :recipientId
            """)
    int updateStatus(@Param("eventId") UUID eventId,
                     @Param("recipientId") UUID recipientId,
                     @Param("status") ChatRealtimeDeliveryStatus status,
                     @Param("deliveredAt") LocalDateTime deliveredAt);
}
