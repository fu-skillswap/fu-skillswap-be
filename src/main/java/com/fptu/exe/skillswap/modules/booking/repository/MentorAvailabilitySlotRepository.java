package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorAvailabilitySlotRepository extends JpaRepository<MentorAvailabilitySlot, UUID> {

    List<MentorAvailabilitySlot> findByMentorProfileUserIdAndStartTimeUtcGreaterThanEqualAndStartTimeUtcLessThanAndIsActiveTrueOrderByStartTimeUtcAsc(
            UUID mentorUserId,
            Instant startTime,
            Instant endTime
    );

    @Query("""
            select slot
            from MentorAvailabilitySlot slot
            join fetch slot.mentorProfile mp
            join fetch mp.user u
            where slot.mentorProfile.userId = :mentorUserId
              and slot.isActive = true
              and slot.startTimeUtc < :endTime
              and slot.endTimeUtc > :startTime
            order by slot.startTimeUtc asc
            """)
    List<MentorAvailabilitySlot> findVisibleSlotsByMentorUserId(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    boolean existsByMentorProfileUserIdAndStartTimeUtcAndEndTimeUtcAndIsActiveTrue(
            UUID mentorUserId,
            Instant startTime,
            Instant endTime
    );

    List<MentorAvailabilitySlot> findByRuleIdAndStartTimeUtcGreaterThanEqualOrderByStartTimeUtcAsc(
            UUID ruleId,
            Instant fromTime
    );

    @Query("""
            select (count(slot) > 0)
            from MentorAvailabilitySlot slot
            where slot.mentorProfile.userId = :mentorUserId
              and slot.isActive = true
              and slot.startTimeUtc < :endTime
              and slot.endTimeUtc > :startTime
            """)
    boolean existsOverlappingActiveSlot(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Modifying
    @Query("""
            update MentorAvailabilitySlot slot
            set slot.isActive = false
            where slot.mentorProfile.userId = :mentorUserId
              and slot.startTimeUtc >= :fromTime
              and slot.isActive = true
            """)
    int deactivateFutureUnbookedSlots(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("fromTime") Instant fromTime
    );

    @Query("""
            select distinct slot from MentorAvailabilitySlot slot
            left join fetch slot.slotServices ss
            left join fetch ss.service
            where slot.mentorProfile.userId = :mentorUserId
              and slot.startTimeUtc < :endTime
              and slot.endTimeUtc > :startTime
            order by slot.startTimeUtc asc
            """)
    List<MentorAvailabilitySlot> findMyManagedSlotsWithServices(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Query("""
            select (count(slot) > 0)
            from MentorAvailabilitySlot slot
            where slot.mentorProfile.userId = :mentorUserId
              and slot.id <> :slotId
              and slot.isActive = true
              and slot.startTimeUtc < :endTime
              and slot.endTimeUtc > :startTime
            """)
    boolean existsOverlappingActiveSlotExcludeSelf(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("slotId") UUID slotId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slot
            from MentorAvailabilitySlot slot
            where slot.id = :slotId
            """)
    Optional<MentorAvailabilitySlot> findByIdForUpdate(@Param("slotId") UUID slotId);

    @Query("""
            select distinct slot from MentorAvailabilitySlot slot
            left join fetch slot.slotServices binding
            left join fetch binding.service
            where slot.template.id = :templateId
              and slot.templateOccurrenceDate >= :fromDate
              and slot.templateOccurrenceDate <= :toDate
            order by slot.templateOccurrenceDate asc
            """)
    List<MentorAvailabilitySlot> findTemplateOccurrences(
            @Param("templateId") UUID templateId,
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);

    @Query("""
            select slot from MentorAvailabilitySlot slot
            where slot.template.id = :templateId and slot.templateOccurrenceDate = :occurrenceDate
            """)
    Optional<MentorAvailabilitySlot> findByTemplateIdAndOccurrenceDate(
            @Param("templateId") UUID templateId,
            @Param("occurrenceDate") java.time.LocalDate occurrenceDate);

    @Query("""
            select slot from MentorAvailabilitySlot slot
            where slot.mentorProfile.userId = :mentorUserId
              and slot.template is not null
              and slot.isActive = true
              and slot.startTimeUtc < :endTime and slot.endTimeUtc > :startTime
            order by slot.template.id asc, slot.templateOccurrenceDate asc
            """)
    List<MentorAvailabilitySlot> findActiveGeneratedOverlaps(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("""
            select slot from MentorAvailabilitySlot slot
            where slot.mentorProfile.userId = :mentorUserId
              and slot.template is null
              and slot.isActive = true
              and slot.startTimeUtc < :endTime and slot.endTimeUtc > :startTime
            order by slot.startTimeUtc asc, slot.id asc
            """)
    List<MentorAvailabilitySlot> findActiveManualOverlaps(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("""
            select distinct slot.mentorProfile.userId
            from MentorAvailabilitySlot slot
            where slot.mentorProfile.userId in :mentorUserIds
              and slot.isActive = true
              and slot.startTimeUtc >= :now
            """)
    List<UUID> findMentorUserIdsWithActiveSlotsInFuture(
            @Param("mentorUserIds") java.util.Collection<UUID> mentorUserIds,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            update MentorAvailabilitySlot slot
            set slot.version = slot.version + 1,
                slot.updatedAtUtc = :updatedAt
            where slot.id in :slotIds
            """)
    int bumpVersions(@Param("slotIds") java.util.Collection<UUID> slotIds,
                     @Param("updatedAt") Instant updatedAt);
}
