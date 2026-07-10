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
}
