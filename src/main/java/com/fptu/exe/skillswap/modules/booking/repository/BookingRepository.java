package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.projection.BookingSegmentPendingCountProjection;
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
            order by
                case
                    when booking.status in :paidStatuses then 0
                    when booking.status = :awaitingPaymentStatus then 1
                    when booking.status = :pendingStatus then 2
                    when booking.status in :cancelledStatuses then 3
                    else 4
                end asc,
                booking.selectedStartTime desc,
                booking.createdAt desc,
                booking.id asc
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentee.id = :menteeUserId
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMenteeBookingsOrderedByDashboardPriority(
            @Param("menteeUserId") UUID menteeUserId,
            @Param("paidStatuses") Collection<BookingStatus> paidStatuses,
            @Param("awaitingPaymentStatus") BookingStatus awaitingPaymentStatus,
            @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("cancelledStatuses") Collection<BookingStatus> cancelledStatuses,
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
            order by
                case
                    when booking.status in :paidStatuses then 0
                    when booking.status = :awaitingPaymentStatus then 1
                    when booking.status = :pendingStatus then 2
                    when booking.status in :cancelledStatuses then 3
                    else 4
                end asc,
                booking.selectedStartTime desc,
                booking.createdAt desc,
                booking.id asc
            """, countQuery = """
            select count(booking.id)
            from Booking booking
            where booking.mentorProfile.userId = :mentorUserId
            """)
    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findMyMentorBookingsOrderedByDashboardPriority(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("paidStatuses") Collection<BookingStatus> paidStatuses,
            @Param("awaitingPaymentStatus") BookingStatus awaitingPaymentStatus,
            @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("cancelledStatuses") Collection<BookingStatus> cancelledStatuses,
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

    boolean existsByMenteeIdAndSlotIdAndStatusIn(UUID menteeId, UUID slotId, Collection<BookingStatus> statuses);

    boolean existsByMenteeIdAndSlotIdAndSelectedStartTimeAndSelectedEndTimeAndStatusIn(
            UUID menteeId,
            UUID slotId,
            java.time.LocalDateTime selectedStartTime,
            java.time.LocalDateTime selectedEndTime,
            Collection<BookingStatus> statuses
    );

    @Query("select booking.slot.id from Booking booking where booking.id = :bookingId")
    Optional<UUID> findSlotIdByBookingId(@Param("bookingId") UUID bookingId);

    List<Booking> findBySlotIdAndStatus(UUID slotId, BookingStatus status);

    long countBySlotIdAndStatus(UUID slotId, BookingStatus status);

    List<Booking> findByMentorProfileUserIdAndStatus(UUID mentorUserId, BookingStatus status);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findByStatusAndSelectedStartTimeBeforeOrderBySelectedStartTimeAsc(BookingStatus status, LocalDateTime selectedStartTimeBefore);

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
              and b.status = 'ACCEPTED'
              and b.selectedStartTime < :endTime
              and b.selectedEndTime > :startTime
            """)
    boolean hasOverlappingAcceptedBooking(
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

    long countBySlotIdAndStatusIn(UUID slotId, Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    List<Booking> findByStatusAndAcceptedAtBeforeOrderByAcceptedAtAsc(BookingStatus status, LocalDateTime acceptedAtBefore);

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
}
