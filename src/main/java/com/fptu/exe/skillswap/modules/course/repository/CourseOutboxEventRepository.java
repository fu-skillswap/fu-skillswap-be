package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CourseOutboxEventRepository extends JpaRepository<CourseOutboxEvent, UUID> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from CourseOutboxEvent event where event.id = :id")
    java.util.Optional<CourseOutboxEvent> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT id FROM course_outbox_events 
            WHERE (status = 'PENDING' OR (status = 'FAILED' AND next_retry_at <= :now)
                   OR (status = 'PROCESSING' AND processing_started_at <= :staleBefore))
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> findClaimableIdsForUpdateSkipLocked(@Param("now") Instant now,
                                                    @Param("staleBefore") Instant staleBefore,
                                                    @Param("limit") int limit);

    @Query("select event from CourseOutboxEvent event where event.aggregateId = :aggregateId and event.eventType = :eventType and event.status in ('PENDING', 'PROCESSING', 'FAILED') order by event.createdAt asc")
    java.util.List<CourseOutboxEvent> findActiveByAggregateIdAndEventType(@Param("aggregateId") UUID aggregateId,
                                                                            @Param("eventType") String eventType);
}
