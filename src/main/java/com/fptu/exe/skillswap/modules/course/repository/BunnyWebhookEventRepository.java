package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BunnyWebhookEventRepository extends JpaRepository<BunnyWebhookEvent, UUID> {
    Optional<BunnyWebhookEvent> findByExternalEventId(String externalEventId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT id FROM bunny_webhook_events 
            WHERE (status = 'PENDING' OR (status = 'FAILED' AND next_retry_at <= :now))
            ORDER BY received_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    java.util.List<UUID> findPendingOrFailedIdsForUpdateSkipLocked(@org.springframework.data.repository.query.Param("now") java.time.Instant now, @org.springframework.data.repository.query.Param("limit") int limit);
}
