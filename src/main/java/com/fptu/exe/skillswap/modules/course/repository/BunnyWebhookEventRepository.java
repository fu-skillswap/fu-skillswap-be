package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BunnyWebhookEventRepository extends JpaRepository<BunnyWebhookEvent, UUID> {
    Optional<BunnyWebhookEvent> findByExternalEventId(String externalEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BunnyWebhookEvent event where event.id = :id")
    Optional<BunnyWebhookEvent> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT id FROM bunny_webhook_events 
            WHERE (status = 'PENDING' OR (status = 'FAILED' AND next_retry_at <= :now)
                   OR (status = 'PROCESSING' AND processing_started_at <= :staleBefore))
            ORDER BY received_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    java.util.List<UUID> findClaimableIdsForUpdateSkipLocked(@Param("now") java.time.Instant now,
                                                              @Param("staleBefore") java.time.Instant staleBefore,
                                                              @Param("limit") int limit);
}
