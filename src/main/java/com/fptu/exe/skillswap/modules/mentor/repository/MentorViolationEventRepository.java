package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface MentorViolationEventRepository extends JpaRepository<MentorViolationEvent, UUID> {

    boolean existsByOperationKey(String operationKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from MentorViolationEvent event where event.id = :id")
    java.util.Optional<MentorViolationEvent> findByIdForUpdate(@Param("id") UUID id);

    Page<MentorViolationEvent> findByMentorUserIdOrderByOccurredAtDesc(UUID mentorUserId, Pageable pageable);

    long countByMentorUserId(UUID mentorUserId);

    @Query("select coalesce(sum(event.points), 0) from MentorViolationEvent event where event.mentorUserId = :mentorUserId and event.reversedAt is null")
    BigDecimal sumPointsByMentorUserId(@Param("mentorUserId") UUID mentorUserId);

    @Query("select coalesce(sum(event.points), 0) from MentorViolationEvent event "
            + "where event.mentorUserId = :mentorUserId and event.reversedAt is null and event.occurredAt >= :windowStart")
    BigDecimal sumActivePointsByMentorUserId(@Param("mentorUserId") UUID mentorUserId,
                                              @Param("windowStart") LocalDateTime windowStart);
}
