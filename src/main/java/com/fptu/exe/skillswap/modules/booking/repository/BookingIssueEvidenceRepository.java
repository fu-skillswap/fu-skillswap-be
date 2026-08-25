package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidence;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingIssueEvidenceRepository extends JpaRepository<BookingIssueEvidence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evidence from BookingIssueEvidence evidence join fetch evidence.booking where evidence.id in :evidenceIds order by evidence.id asc")
    List<BookingIssueEvidence> findAllByIdInForUpdate(@Param("evidenceIds") Collection<UUID> evidenceIds);

    @Query("select evidence from BookingIssueEvidence evidence where evidence.booking.id = :bookingId and evidence.state in :states order by evidence.attachedAtUtc asc, evidence.id asc")
    List<BookingIssueEvidence> findByBookingIdAndStateInOrderByAttachedAtUtcAsc(
            @Param("bookingId") UUID bookingId,
            @Param("states") Collection<BookingIssueEvidenceState> states);

    @Query("select evidence from BookingIssueEvidence evidence join fetch evidence.booking booking where evidence.id = :evidenceId")
    Optional<BookingIssueEvidence> findWithBookingById(@Param("evidenceId") UUID evidenceId);

    Optional<BookingIssueEvidence> findByUploadIntentId(UUID uploadIntentId);

    @Query("select evidence from BookingIssueEvidence evidence join fetch evidence.booking booking where evidence.state in :states and booking.issueResolvedAtUtc is not null and booking.issueResolvedAtUtc < :resolvedBefore order by booking.issueResolvedAtUtc asc, evidence.id asc")
    List<BookingIssueEvidence> findTop100ReadyForRetentionDeletion(
            @Param("states") Collection<BookingIssueEvidenceState> states,
            @Param("resolvedBefore") Instant resolvedBefore);
}
