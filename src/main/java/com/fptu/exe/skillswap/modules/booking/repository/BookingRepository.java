package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.projection.BookingSegmentPendingCountProjection;
import com.fptu.exe.skillswap.modules.booking.repository.projection.PendingBookingServiceCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findByMenteeId(UUID menteeUserId, Pageable pageable);

    @Query(value = """
            select booking
            from Booking booking
            where booking.mentee.id = :menteeUserId
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            order by
                case
                    when booking.status in :primaryActionStatuses then 0
                    when booking.status in :secondaryActionStatuses then 1
                    when booking.status in :upcomingStatuses then 2
                    when booking.status in :cancelledStatuses then 3
                    else 4
                end asc,
                booking.selectedStartTime asc,
                booking.createdAt desc,
                booking.id asc
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentee.id = :menteeUserId
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMenteeBookingsOrderedByDashboardPriority(
            @Param("menteeUserId") UUID menteeUserId,
            @Param("primaryActionStatuses") Collection<BookingStatus> primaryActionStatuses,
            @Param("secondaryActionStatuses") Collection<BookingStatus> secondaryActionStatuses,
            @Param("upcomingStatuses") Collection<BookingStatus> upcomingStatuses,
            @Param("cancelledStatuses") Collection<BookingStatus> cancelledStatuses,
            @Param("startTimeStart") LocalDateTime startTimeStart,
            @Param("startTimeEnd") LocalDateTime startTimeEnd,
            Pageable pageable
    );

    @Query(value = """
            select booking
            from Booking booking
            where booking.mentee.id = :menteeUserId
              and booking.selectedStartTimeUtc between :startTimeStartUtc and :startTimeEndUtc
            order by
                case
                    when booking.status in :primaryActionStatuses then 0
                    when booking.status in :secondaryActionStatuses then 1
                    when booking.status in :upcomingStatuses then 2
                    when booking.status in :cancelledStatuses then 3
                    else 4
                end asc,
                booking.selectedStartTimeUtc asc,
                booking.createdAtUtc desc,
                booking.id asc
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentee.id = :menteeUserId
              and booking.selectedStartTimeUtc between :startTimeStartUtc and :startTimeEndUtc
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMenteeBookingsOrderedByDashboardPriorityUtc(
            @Param("menteeUserId") UUID menteeUserId,
            @Param("primaryActionStatuses") Collection<BookingStatus> primaryActionStatuses,
            @Param("secondaryActionStatuses") Collection<BookingStatus> secondaryActionStatuses,
            @Param("upcomingStatuses") Collection<BookingStatus> upcomingStatuses,
            @Param("cancelledStatuses") Collection<BookingStatus> cancelledStatuses,
            @Param("startTimeStartUtc") Instant startTimeStartUtc,
            @Param("startTimeEndUtc") Instant startTimeEndUtc,
            Pageable pageable
    );

    @Query(value = """
            select booking
            from Booking booking
            where booking.mentee.id = :menteeUserId
              and booking.status = :status
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentee.id = :menteeUserId
              and booking.status = :status
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMenteeBookingsByStatusAndDateRange(
            @Param("menteeUserId") UUID menteeUserId,
            @Param("status") BookingStatus status,
            @Param("startTimeStart") LocalDateTime startTimeStart,
            @Param("startTimeEnd") LocalDateTime startTimeEnd,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findByMenteeIdAndStatus(UUID menteeUserId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findByMentorProfileUserId(UUID mentorUserId, Pageable pageable);

    @Query(value = """
            select booking
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            order by
                case
                    when booking.status in :primaryActionStatuses then 0
                    when booking.status in :secondaryActionStatuses then 1
                    when booking.status in :upcomingStatuses then 2
                    when booking.status in :cancelledStatuses then 3
                    else 4
                end asc,
                booking.selectedStartTime asc,
                booking.createdAt desc,
                booking.id asc
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMentorBookingsOrderedByDashboardPriority(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("primaryActionStatuses") Collection<BookingStatus> primaryActionStatuses,
            @Param("secondaryActionStatuses") Collection<BookingStatus> secondaryActionStatuses,
            @Param("upcomingStatuses") Collection<BookingStatus> upcomingStatuses,
            @Param("cancelledStatuses") Collection<BookingStatus> cancelledStatuses,
            @Param("startTimeStart") LocalDateTime startTimeStart,
            @Param("startTimeEnd") LocalDateTime startTimeEnd,
            Pageable pageable
    );

    @Query(value = """
            select booking
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
              and booking.selectedStartTimeUtc between :startTimeStartUtc and :startTimeEndUtc
            order by
                case
                    when booking.status in :primaryActionStatuses then 0
                    when booking.status in :secondaryActionStatuses then 1
                    when booking.status in :upcomingStatuses then 2
                    when booking.status in :cancelledStatuses then 3
                    else 4
                end asc,
                booking.selectedStartTimeUtc asc,
                booking.createdAtUtc desc,
                booking.id asc
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
              and booking.selectedStartTimeUtc between :startTimeStartUtc and :startTimeEndUtc
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMentorBookingsOrderedByDashboardPriorityUtc(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("primaryActionStatuses") Collection<BookingStatus> primaryActionStatuses,
            @Param("secondaryActionStatuses") Collection<BookingStatus> secondaryActionStatuses,
            @Param("upcomingStatuses") Collection<BookingStatus> upcomingStatuses,
            @Param("cancelledStatuses") Collection<BookingStatus> cancelledStatuses,
            @Param("startTimeStartUtc") Instant startTimeStartUtc,
            @Param("startTimeEndUtc") Instant startTimeEndUtc,
            Pageable pageable
    );

    @Query(value = """
            select booking
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
              and booking.status = :status
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
              and booking.status = :status
              and booking.selectedStartTime between :startTimeStart and :startTimeEnd
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMentorBookingsByStatusAndDateRange(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("status") BookingStatus status,
            @Param("startTimeStart") LocalDateTime startTimeStart,
            @Param("startTimeEnd") LocalDateTime startTimeEnd,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findByMentorProfileUserIdAndStatus(UUID mentorUserId, BookingStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Optional<Booking> findById(UUID bookingId);

    @Query(value = """
            select booking
            from Booking booking
            where (:status is null or booking.status = :status)
              and (:mentorUserId is null or booking.mentorProfile.userId = :mentorUserId)
              and (:menteeUserId is null or booking.mentee.id = :menteeUserId)
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where (:status is null or booking.status = :status)
              and (:mentorUserId is null or booking.mentorProfile.userId = :mentorUserId)
              and (:menteeUserId is null or booking.mentee.id = :menteeUserId)
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> searchForAdmin(
            @Param("status") BookingStatus status,
            @Param("mentorUserId") UUID mentorUserId,
            @Param("menteeUserId") UUID menteeUserId,
            Pageable pageable
    );

    @Query("""
            select booking
            from Booking booking
            where booking.id = :bookingId
            """)
    Optional<Booking> findByIdForMentorDecision(@Param("bookingId") UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.id = :bookingId
            """)
    Optional<Booking> findByIdForCancellation(@Param("bookingId") UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.id = :bookingId
            """)
    Optional<Booking> findByIdForSessionUpdate(@Param("bookingId") UUID bookingId);



    long countByMenteeIdAndStatus(UUID menteeId, BookingStatus status);

    long countByMenteeId(UUID menteeId);

    boolean existsByMenteeIdAndServiceIdAndStatusIn(UUID menteeId, UUID serviceId, Collection<BookingStatus> statuses);

    @Query("select distinct booking.mentee.id from Booking booking where booking.service.id in :serviceIds and booking.status in :statuses")
    List<UUID> findDistinctMenteeIdsByServiceIdsAndStatusIn(
            @Param("serviceIds") Collection<UUID> serviceIds,
            @Param("statuses") Collection<BookingStatus> statuses
    );

    long countByMentorProfileUserId(UUID mentorUserId);

    boolean existsByMentorProfileUserIdAndStatusAndSelectedStartTimeAfter(
            UUID mentorUserId,
            BookingStatus status,
            LocalDateTime selectedStartTimeAfter
    );

    @Query("""
            select count(booking.id) > 0
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
              and booking.status = :status
              and booking.selectedStartTimeUtc > :selectedStartTimeAfterUtc
            """)
    boolean existsByMentorProfileUserIdAndStatusAndSelectedStartTimeUtcAfter(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("status") BookingStatus status,
            @Param("selectedStartTimeAfterUtc") Instant selectedStartTimeAfterUtc
    );

    boolean existsByMenteeIdAndSlotIdAndStatusIn(UUID menteeId, UUID slotId, Collection<BookingStatus> statuses);

    boolean existsByMenteeIdAndSlotIdAndSelectedStartTimeAndSelectedEndTimeAndStatusIn(
            UUID menteeId,
            UUID slotId,
            java.time.LocalDateTime selectedStartTime,
            java.time.LocalDateTime selectedEndTime,
            Collection<BookingStatus> statuses
    );

    boolean existsByMenteeIdAndSlotIdAndSelectedStartTimeUtcAndSelectedEndTimeUtcAndStatusIn(
            UUID menteeId,
            UUID slotId,
            Instant selectedStartTimeUtc,
            Instant selectedEndTimeUtc,
            Collection<BookingStatus> statuses
    );

    @Query("select booking.slot.id from Booking booking where booking.id = :bookingId")
    Optional<UUID> findSlotIdByBookingId(@Param("bookingId") UUID bookingId);

    @Query("select booking.mentee.id from Booking booking where booking.id = :bookingId")
    Optional<UUID> findMenteeIdByBookingId(@Param("bookingId") UUID bookingId);

    List<Booking> findBySlotIdAndStatus(UUID slotId, BookingStatus status);

    List<Booking> findByServiceIdAndStatus(UUID serviceId, BookingStatus status);
    long countBySlotIdAndStatus(UUID slotId, BookingStatus status);

    List<Booking> findByMentorProfileUserIdAndStatus(UUID mentorUserId, BookingStatus status);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findByStatusAndSelectedStartTimeBeforeOrderBySelectedStartTimeAsc(BookingStatus status, LocalDateTime selectedStartTimeBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findByStatusAndPendingExpireAtLessThanEqualOrderByPendingExpireAtAsc(
            BookingStatus status,
            LocalDateTime pendingExpireAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking from Booking booking
            join fetch booking.mentee
            join fetch booking.mentorProfile
            left join fetch booking.slot
            where booking.status = :status
              and booking.pendingExpireAt <= :pendingExpireAt
            order by booking.pendingExpireAt asc, booking.id asc
            """)
    List<Booking> findPendingExpiryCandidatesForUpdate(
            @Param("status") BookingStatus status,
            @Param("pendingExpireAt") LocalDateTime pendingExpireAt
    );

    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.status in :statuses
              and booking.selectedStartTime >= :startInclusive
              and booking.selectedStartTime < :endExclusive
            order by booking.selectedStartTime asc, booking.id asc
            """)
    List<Booking> findConfirmedBookingsStartingBetween(
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.status in :statuses
              and booking.selectedStartTimeUtc >= :startInclusiveUtc
              and booking.selectedStartTimeUtc < :endExclusiveUtc
            order by booking.selectedStartTimeUtc asc, booking.id asc
            """)
    List<Booking> findConfirmedBookingsStartingBetweenUtc(
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startInclusiveUtc") Instant startInclusiveUtc,
            @Param("endExclusiveUtc") Instant endExclusiveUtc
    );

    @Query("""
            select booking.mentorProfile.userId as mentorUserId,
                   mentorUser.email as mentorEmail,
                   mentorUser.fullName as mentorName,
                   coalesce(booking.serviceTitleSnapshot, service.title, 'Dịch vụ mentoring') as serviceTitle,
                   count(booking.id) as pendingCount
            from Booking booking
            join booking.mentorProfile mentorProfile
            join mentorProfile.user mentorUser
            left join booking.service service
            where booking.status = :status
            group by booking.mentorProfile.userId,
                     mentorUser.email,
                     mentorUser.fullName,
                     booking.serviceTitleSnapshot,
                     service.title
            order by mentorUser.email asc, serviceTitle asc
            """)
    List<PendingBookingServiceCountProjection> countPendingRequestsGroupedByMentorAndService(
            @Param("status") BookingStatus status
    );

    @Query("""
            select count(b.id)
            from Booking b
            where b.slot.id = :slotId
              and b.status = :status
              and b.selectedStartTime = :selectedStartTime
              and b.selectedEndTime = :selectedEndTime
            """)
    long countBySlotIdAndExactSegmentAndStatus(
            @Param("slotId") UUID slotId,
            @Param("selectedStartTime") java.time.LocalDateTime selectedStartTime,
            @Param("selectedEndTime") java.time.LocalDateTime selectedEndTime,
            @Param("status") BookingStatus status
    );

    @Query("""
            select count(b.id)
            from Booking b
            where b.slot.id = :slotId
              and b.status = :status
              and b.selectedStartTimeUtc = :selectedStartTimeUtc
              and b.selectedEndTimeUtc = :selectedEndTimeUtc
            """)
    long countBySlotIdAndExactSegmentAndStatusUtc(
            @Param("slotId") UUID slotId,
            @Param("selectedStartTimeUtc") Instant selectedStartTimeUtc,
            @Param("selectedEndTimeUtc") Instant selectedEndTimeUtc,
            @Param("status") BookingStatus status
    );

    @Query("""
            select count(b.id) > 0
            from Booking b
            where b.slot.id = :slotId
              and b.status = :status
              and b.selectedStartTime < :endTime
              and b.selectedEndTime > :startTime
            """)
    boolean existsOverlappingBySlotIdAndStatus(
            @Param("slotId") UUID slotId,
            @Param("status") BookingStatus status,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime
    );

    @Query("""
            select count(b.id) > 0
            from Booking b
            where b.slot.id = :slotId
              and b.status in :statuses
              and b.selectedStartTime < :endTime
              and b.selectedEndTime > :startTime
            """)
    boolean existsOverlappingBySlotIdAndStatusIn(
            @Param("slotId") UUID slotId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime
    );

    @Query("""
            select count(b.id) > 0
            from Booking b
            where b.slot.id = :slotId
              and b.status in :statuses
              and b.selectedStartTimeUtc < :endTimeUtc
              and b.selectedEndTimeUtc > :startTimeUtc
            """)
    boolean existsOverlappingBySlotIdAndStatusInUtc(
            @Param("slotId") UUID slotId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startTimeUtc") Instant startTimeUtc,
            @Param("endTimeUtc") Instant endTimeUtc
    );

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    @Query("""
            select b
            from Booking b
            where b.slot.id = :slotId
              and b.status = :status
            order by b.selectedStartTime asc, b.id asc
            """)
    List<Booking> findBySlotIdAndStatusOrderBySelectedStartTimeAsc(
            @Param("slotId") UUID slotId,
            @Param("status") BookingStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.slot.id = :slotId
              and booking.status = :status
              and booking.selectedStartTime < :endTime
              and booking.selectedEndTime > :startTime
            order by booking.id asc
            """)
    List<Booking> findOverlappingBySlotIdAndStatusForUpdate(
            @Param("slotId") UUID slotId,
            @Param("status") BookingStatus status,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.slot.id = :slotId
              and booking.status = :status
              and booking.selectedStartTimeUtc < :endTimeUtc
              and booking.selectedEndTimeUtc > :startTimeUtc
            order by booking.id asc
            """)
    List<Booking> findOverlappingBySlotIdAndStatusForUpdateUtc(
            @Param("slotId") UUID slotId,
            @Param("status") BookingStatus status,
            @Param("startTimeUtc") Instant startTimeUtc,
            @Param("endTimeUtc") Instant endTimeUtc
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.mentee.id = :menteeId
              and booking.status in :statuses
              and booking.selectedStartTime < :endTime
              and booking.selectedEndTime > :startTime
            order by booking.id asc
            """)
    List<Booking> findMenteeOverlappingBookingsForUpdate(
            @Param("menteeId") UUID menteeId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            left join fetch booking.slot slot
            where booking.mentee.id = :menteeId
              and booking.status in :statuses
              and booking.selectedStartTimeUtc < :endTimeUtc
              and booking.selectedEndTimeUtc > :startTimeUtc
            order by booking.id asc
            """)
    List<Booking> findMenteeOverlappingBookingsForUpdateUtc(
            @Param("menteeId") UUID menteeId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startTimeUtc") Instant startTimeUtc,
            @Param("endTimeUtc") Instant endTimeUtc
    );

    @Query("""
            select b.selectedStartTime as startTime,
            b.selectedEndTime as endTime,
            count(b.id) as pendingCount
            from Booking b
            where b.slot.id = :slotId
              and b.status = :status
            group by b.selectedStartTime, b.selectedEndTime
            """)
    List<BookingSegmentPendingCountProjection> countPendingSegmentsBySlotId(
            @Param("slotId") UUID slotId,
            @Param("status") BookingStatus status
    );

    @Query("""
            select booking
            from Booking booking
            where booking.mentee.id = :menteeId
              and booking.status = :status
              and booking.id != :excludeBookingId
              and booking.selectedStartTime < :endTime
              and booking.selectedEndTime > :startTime
            """)
    List<Booking> findOverlappingPendingBookingsForMentee(
            @Param("menteeId") UUID menteeId,
            @Param("status") BookingStatus status,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime,
            @Param("excludeBookingId") UUID excludeBookingId
    );

    @Query("""
            select count(b.id) > 0
            from Booking b
            where b.mentee.id = :menteeId
              and b.status = 'PAID'
              and b.selectedStartTime < :endTime
              and b.selectedEndTime > :startTime
            """)
    boolean hasOverlappingPaidBooking(
            @Param("menteeId") UUID menteeId,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime
    );

    @Query("""
            select count(b.id) > 0
            from Booking b
            where b.mentee.id = :menteeId
              and b.status in :statuses
              and b.selectedStartTime < :endTime
              and b.selectedEndTime > :startTime
            """)
    boolean hasOverlappingBookingByStatuses(
            @Param("menteeId") UUID menteeId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime
    );

    @Query("""
            select count(b.id) > 0
            from Booking b
            where b.mentee.id = :menteeId
              and b.status in :statuses
              and b.selectedStartTimeUtc < :endTimeUtc
              and b.selectedEndTimeUtc > :startTimeUtc
            """)
    boolean hasOverlappingBookingByStatusesUtc(
            @Param("menteeId") UUID menteeId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("startTimeUtc") Instant startTimeUtc,
            @Param("endTimeUtc") Instant endTimeUtc
    );

    long countBySlotIdAndStatusIn(UUID slotId, Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findByStatusAndAcceptedAtBeforeOrderByAcceptedAtAsc(BookingStatus status, LocalDateTime acceptedAtBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and (
                    booking.acceptedAt <= :acceptedAtCutoff
                    or booking.selectedStartTime <= :startTimeCutoff
              )
            order by booking.acceptedAt asc, booking.selectedStartTime asc, booking.id asc
            """)
    List<Booking> findAwaitingPaymentExpiryCandidates(
            @Param("status") BookingStatus status,
            @Param("acceptedAtCutoff") LocalDateTime acceptedAtCutoff,
            @Param("startTimeCutoff") LocalDateTime startTimeCutoff
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b
            set b.status = :expiredStatus,
                b.rejectedAt = :now,
                b.rejectReason = :reason,
                b.updatedAt = :now
            where b.status = :pendingStatus
              and b.selectedStartTime < :now
            """)
    int bulkExpireStalePendingBookings(
            @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("expiredStatus") BookingStatus expiredStatus,
            @Param("now") java.time.LocalDateTime now,
            @Param("reason") String reason
    );

    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and booking.selectedEndTime between :startInclusive and :endExclusive
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service"})
    List<Booking> findBookingsAboutToAutoClose(
            @Param("status") BookingStatus status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and booking.selectedEndTimeUtc between :startInclusiveUtc and :endExclusiveUtc
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service"})
    List<Booking> findBookingsAboutToAutoCloseUtc(
            @Param("status") BookingStatus status,
            @Param("startInclusiveUtc") Instant startInclusiveUtc,
            @Param("endExclusiveUtc") Instant endExclusiveUtc
    );

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(
            BookingStatus status, LocalDateTime selectedEndTimeBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findTop100ByStatusAndCompletedAtBeforeOrderByCompletedAtAsc(
            BookingStatus status, LocalDateTime completedAtBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findTop100ByStatusAndIssueSubmittedAtBeforeOrderByIssueSubmittedAtAsc(
            BookingStatus status, LocalDateTime issueSubmittedAtBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findTop100ByStatusAndIssueSubmittedAtBeforeAndAdminSlaWarningSentAtIsNullAndIssueResolvedAtIsNullOrderByIssueSubmittedAtAsc(
            BookingStatus status, LocalDateTime issueSubmittedAtBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findByStatusAndPendingExpireAtUtcLessThanEqualOrderByPendingExpireAtUtcAsc(
            BookingStatus status, Instant pendingExpireAtUtc);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and (
                    booking.acceptedAtUtc <= :acceptedAtCutoff
                    or booking.selectedStartTimeUtc <= :startTimeCutoff
              )
            order by booking.acceptedAtUtc asc, booking.selectedStartTimeUtc asc, booking.id asc
            """)
    List<Booking> findAwaitingPaymentExpiryCandidatesUtc(
            @Param("status") BookingStatus status,
            @Param("acceptedAtCutoff") Instant acceptedAtCutoff,
            @Param("startTimeCutoff") Instant startTimeCutoff
    );

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and booking.selectedEndTimeUtc < :selectedEndTimeUtcBefore
            order by booking.selectedEndTimeUtc asc
            """)
    List<Booking> findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(
            @Param("status") BookingStatus status,
            @Param("selectedEndTimeUtcBefore") Instant selectedEndTimeUtcBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and booking.completedAtUtc < :completedAtUtcBefore
            order by booking.completedAtUtc asc
            """)
    List<Booking> findTop100ByStatusAndCompletedAtUtcBeforeOrderByCompletedAtUtcAsc(
            @Param("status") BookingStatus status,
            @Param("completedAtUtcBefore") Instant completedAtUtcBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and booking.issueSubmittedAtUtc < :issueSubmittedAtUtcBefore
            order by booking.issueSubmittedAtUtc asc
            """)
    List<Booking> findTop100ByStatusAndIssueSubmittedAtUtcBeforeOrderByIssueSubmittedAtUtcAsc(
            @Param("status") BookingStatus status,
            @Param("issueSubmittedAtUtcBefore") Instant issueSubmittedAtUtcBefore);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    @Query("""
            select booking
            from Booking booking
            where booking.status = :status
              and booking.issueSubmittedAtUtc < :issueSubmittedAtUtcBefore
              and booking.adminSlaWarningSentAtUtc is null
              and booking.issueResolvedAtUtc is null
            order by booking.issueSubmittedAtUtc asc
            """)
    List<Booking> findTop100ByStatusAndIssueSubmittedAtUtcBeforeAndAdminSlaWarningSentAtIsNullAndIssueResolvedAtIsNullOrderByIssueSubmittedAtUtcAsc(
            @Param("status") BookingStatus status,
            @Param("issueSubmittedAtUtcBefore") Instant issueSubmittedAtUtcBefore);
}

