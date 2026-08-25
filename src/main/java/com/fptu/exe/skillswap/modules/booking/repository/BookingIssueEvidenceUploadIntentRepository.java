package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceUploadIntent;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceUploadIntentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingIssueEvidenceUploadIntentRepository extends JpaRepository<BookingIssueEvidenceUploadIntent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from BookingIssueEvidenceUploadIntent intent join fetch intent.booking where intent.id = :intentId")
    Optional<BookingIssueEvidenceUploadIntent> findByIdForUpdate(@Param("intentId") UUID intentId);

    List<BookingIssueEvidenceUploadIntent> findTop100ByStatusInAndExpiresAtUtcBeforeOrderByExpiresAtUtcAsc(
            List<BookingIssueEvidenceUploadIntentStatus> statuses, Instant expiresAtUtc);
}
