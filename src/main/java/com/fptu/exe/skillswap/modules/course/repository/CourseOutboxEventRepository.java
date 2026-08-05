package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CourseOutboxEventRepository extends JpaRepository<CourseOutboxEvent, UUID> {

    @Query(value = """
            SELECT id FROM course_outbox_events 
            WHERE (status = 'PENDING' OR (status = 'FAILED' AND next_retry_at <= :now))
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> findPendingOrFailedIdsForUpdateSkipLocked(@Param("now") Instant now, @Param("limit") int limit);
}
