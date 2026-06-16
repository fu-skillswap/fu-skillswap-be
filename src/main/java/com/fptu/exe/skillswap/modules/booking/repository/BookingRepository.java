package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findByMenteeId(UUID menteeUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findByMenteeIdAndStatus(UUID menteeUserId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"mentee", "mentorProfile", "mentorProfile.user", "service", "slot"})
    Page<Booking> findByMentorProfileUserId(UUID mentorUserId, Pageable pageable);

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

    List<Booking> findBySlotIdAndStatus(UUID slotId, BookingStatus status);

    List<Booking> findByMentorProfileUserIdAndStatus(UUID mentorUserId, BookingStatus status);

    @Query("""
            select booking
            from Booking booking
            where booking.mentee.id = :menteeId
              and booking.status = :status
              and booking.id != :excludeBookingId
              and booking.requestedStartTime < :endTime
              and booking.requestedEndTime > :startTime
            """)
    List<Booking> findOverlappingPendingBookingsForMentee(
            @Param("menteeId") UUID menteeId,
            @Param("status") BookingStatus status,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime,
            @Param("excludeBookingId") UUID excludeBookingId
    );
}
