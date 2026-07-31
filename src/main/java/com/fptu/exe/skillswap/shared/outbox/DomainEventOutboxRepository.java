package com.fptu.exe.skillswap.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DomainEventOutboxRepository extends JpaRepository<DomainEventOutbox, UUID> {

    List<DomainEventOutbox> findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            DomainEventOutboxStatus status,
            LocalDateTime availableAt
    );

    List<DomainEventOutbox> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType,
            UUID aggregateId
    );

    @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM domain_event_outbox WHERE status = 'PUBLISHED' AND published_at < :publishedBefore ORDER BY published_at ASC, id ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<UUID> findExpiredPublishedIdsForUpdateSkipLocked(@org.springframework.data.repository.query.Param("publishedBefore") LocalDateTime publishedBefore, @org.springframework.data.repository.query.Param("batchSize") int batchSize);

    @org.springframework.data.jpa.repository.Query(value = "SELECT id FROM domain_event_outbox WHERE status = 'FAILED' AND attempt_count >= :maxAttempts AND available_at < :failedBefore ORDER BY available_at ASC, id ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<UUID> findExpiredFailedIdsForUpdateSkipLocked(@org.springframework.data.repository.query.Param("failedBefore") LocalDateTime failedBefore, @org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts, @org.springframework.data.repository.query.Param("batchSize") int batchSize);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "DELETE FROM domain_event_outbox WHERE id IN (:ids) AND status = 'PUBLISHED' AND published_at < :publishedBefore", nativeQuery = true)
    int deletePublishedByIdsAndCutoff(@org.springframework.data.repository.query.Param("ids") List<UUID> ids, @org.springframework.data.repository.query.Param("publishedBefore") LocalDateTime publishedBefore);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "DELETE FROM domain_event_outbox WHERE id IN (:ids) AND status = 'FAILED' AND attempt_count >= :maxAttempts AND available_at < :failedBefore", nativeQuery = true)
    int deleteFailedByIdsAndCutoff(@org.springframework.data.repository.query.Param("ids") List<UUID> ids, @org.springframework.data.repository.query.Param("failedBefore") LocalDateTime failedBefore, @org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts);
}
