package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRescheduleRequestRepository extends JpaRepository<BookingRescheduleRequest, UUID> {

    @EntityGraph(attributePaths = {"booking", "booking.mentee", "booking.mentorProfile", "booking.mentorProfile.user", "booking.service", "currentSlot", "proposedSlot"})
    List<BookingRescheduleRequest> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.mentee", "booking.mentorProfile", "booking.mentorProfile.user", "booking.service", "currentSlot", "proposedSlot"})
    Optional<BookingRescheduleRequest> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(UUID bookingId, BookingRescheduleStatus status);

    boolean existsByBookingIdAndStatus(UUID bookingId, BookingRescheduleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from BookingRescheduleRequest request
            join fetch request.booking booking
            join fetch booking.mentee mentee
            join fetch booking.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            left join fetch booking.service service
            join fetch request.currentSlot currentSlot
            join fetch request.proposedSlot proposedSlot
            where request.id = :requestId
            """)
    Optional<BookingRescheduleRequest> findByIdForUpdate(@Param("requestId") UUID requestId);

    @EntityGraph(attributePaths = {"booking", "booking.mentee", "booking.mentorProfile", "booking.mentorProfile.user", "booking.service", "currentSlot", "proposedSlot"})
    @Query("""
            select request
            from BookingRescheduleRequest request
            where request.status = :status
              and request.booking.selectedStartTime <= :deadline
            order by request.createdAt asc
            """)
    List<BookingRescheduleRequest> findExpirablePendingRequests(
            @Param("status") BookingRescheduleStatus status,
            @Param("deadline") LocalDateTime deadline
    );
}
