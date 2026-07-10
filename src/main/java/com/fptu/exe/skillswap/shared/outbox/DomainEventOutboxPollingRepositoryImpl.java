package com.fptu.exe.skillswap.shared.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DomainEventOutboxPollingRepositoryImpl implements DomainEventOutboxPollingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public java.util.List<java.util.UUID> findNextPendingBatchIds(int limit) {
        return entityManager.createNativeQuery("""
                SELECT id
                FROM domain_event_outbox
                WHERE status = 'PENDING'
                  AND available_at <= NOW()
                ORDER BY created_at ASC
                LIMIT ?1
                FOR UPDATE SKIP LOCKED
                """, java.util.UUID.class)
                .setParameter(1, limit)
                .getResultList();
    }

    @Override
    public int reserveBatch(java.util.List<java.util.UUID> ids, java.time.LocalDateTime nextAvailableAt) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return entityManager.createNativeQuery("""
                UPDATE domain_event_outbox
                SET available_at = ?1,
                    updated_at = NOW()
                WHERE id IN (?2)
                  AND status = 'PENDING'
                  AND available_at <= NOW()
                """)
                .setParameter(1, nextAvailableAt)
                .setParameter(2, ids)
                .executeUpdate();
    }
}
