package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionKind;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingIssueResolutionRepository extends JpaRepository<BookingIssueResolution, UUID> {

    Optional<BookingIssueResolution> findFirstByBookingIdAndResolutionKindOrderByCreatedAtUtcDesc(
            UUID bookingId,
            BookingIssueResolutionKind resolutionKind
    );

    Optional<BookingIssueResolution> findFirstByBookingIdAndResolutionKindAndStatusOrderByCreatedAtUtcDesc(
            UUID bookingId,
            BookingIssueResolutionKind resolutionKind,
            BookingIssueResolutionStatus status
    );

    boolean existsByReversalOfResolutionIdAndResolutionKind(
            UUID reversalOfResolutionId,
            BookingIssueResolutionKind resolutionKind
    );
}
