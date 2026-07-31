package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupSessionRepository extends JpaRepository<GroupSession, UUID> {

    @EntityGraph(attributePaths = {"service", "sourceSlot", "mentorProfile"})
    List<GroupSession> findByServiceIdAndMentorProfileUserIdOrderByScheduledStartAtAsc(UUID serviceId, UUID mentorUserId);

    @EntityGraph(attributePaths = {"service", "sourceSlot", "mentorProfile"})
    Optional<GroupSession> findByIdAndMentorProfileUserId(UUID id, UUID mentorUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from GroupSession session
            join fetch session.service
            join fetch session.sourceSlot
            join fetch session.mentorProfile
            where session.id = :id
              and session.mentorProfile.userId = :mentorUserId
            """)
    Optional<GroupSession> findOwnedByIdForUpdate(
            @Param("id") UUID id,
            @Param("mentorUserId") UUID mentorUserId
    );

    @EntityGraph(attributePaths = {"service", "sourceSlot", "mentorProfile", "mentorProfile.user"})
    Optional<GroupSession> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from GroupSession session
            join fetch session.service
            join fetch session.sourceSlot
            join fetch session.mentorProfile mentorProfile
            join fetch mentorProfile.user
            where session.id = :id
            """)
    Optional<GroupSession> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select session
            from GroupSession session
            join fetch session.mentorProfile mentorProfile
            join fetch mentorProfile.user
            where session.status = :openStatus
              and session.registrationStatus = :registrationOpen
              and session.scheduledStartAt >= :fromAt
              and (:mentorUserId is null or mentorProfile.userId = :mentorUserId)
              and (:serviceId is null or session.service.id = :serviceId)
              and (:cursorStartAt is null or session.scheduledStartAt > :cursorStartAt
                   or (session.scheduledStartAt = :cursorStartAt and session.id > :cursorId))
            order by session.scheduledStartAt asc, session.id asc
            """)
    List<GroupSession> findPublicWindow(
            @Param("openStatus") GroupSessionStatus openStatus,
            @Param("registrationOpen") GroupSessionRegistrationStatus registrationOpen,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("mentorUserId") UUID mentorUserId,
            @Param("serviceId") UUID serviceId,
            @Param("cursorStartAt") LocalDateTime cursorStartAt,
            @Param("cursorId") UUID cursorId,
            org.springframework.data.domain.Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from GroupSession session
            where session.mentorProfile.userId = :mentorUserId
              and session.status in :statuses
              and session.scheduledStartAt < :endAt
              and session.scheduledEndAt > :startAt
            order by session.id asc
            """)
    List<GroupSession> findActiveOverlapsForUpdate(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("statuses") Collection<GroupSessionStatus> statuses,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
            select session
            from GroupSession session
            where session.mentorProfile.userId = :mentorUserId
              and session.status in :statuses
              and session.scheduledStartAt < :endAt
              and session.scheduledEndAt > :startAt
            order by session.scheduledStartAt asc
            """)
    List<GroupSession> findActiveOverlaps(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("statuses") Collection<GroupSessionStatus> statuses,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query("""
            select session.id
            from GroupSession session
            where session.status in (:openStatus, :inProgressStatus)
              and (
                    (session.status = :openStatus and session.scheduledStartAt <= :now)
                 or (session.status = :inProgressStatus and session.scheduledEndAt <= :now)
                 or (session.registrationStatus = :registrationOpen and session.registrationClosesAt <= :now)
              )
            order by session.scheduledStartAt asc, session.id asc
            """)
    List<UUID> findLifecycleCandidates(
            @Param("openStatus") GroupSessionStatus openStatus,
            @Param("inProgressStatus") GroupSessionStatus inProgressStatus,
            @Param("registrationOpen") GroupSessionRegistrationStatus registrationOpen,
            @Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            select session
            from GroupSession session
            where session.status in :statuses
            order by session.scheduledStartAt asc, session.id asc
            """)
    List<GroupSession> findSeatReconciliationCandidates(
            @Param("statuses") Collection<GroupSessionStatus> statuses,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("select session.id from GroupSession session where session.status = :status and session.scheduledEndAt <= :endedBefore order by session.scheduledEndAt asc")
    List<UUID> findCompletedBefore(
            @Param("status") GroupSessionStatus status,
            @Param("endedBefore") LocalDateTime endedBefore,
            org.springframework.data.domain.Pageable pageable);

    @Query("select session from GroupSession session where session.status in :statuses order by session.createdAt asc")
    List<GroupSession> findExperienceBackfillCandidates(
            @Param("statuses") Collection<GroupSessionStatus> statuses,
            org.springframework.data.domain.Pageable pageable);
}
