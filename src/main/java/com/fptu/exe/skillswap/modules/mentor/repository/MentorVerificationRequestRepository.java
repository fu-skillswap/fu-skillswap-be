package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorVerificationRequestRepository extends JpaRepository<MentorVerificationRequest, UUID> {

    @Override
    Optional<MentorVerificationRequest> findById(UUID id);

    long countByStatus(VerificationStatus status);

    Optional<MentorVerificationRequest> findFirstByMentorUserIdAndStatusInOrderByCreatedAtDesc(
            UUID mentorUserId,
            Collection<VerificationStatus> statuses
    );

    Optional<MentorVerificationRequest> findFirstByMentorUserIdOrderByCreatedAtDesc(UUID mentorUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from MentorVerificationRequest r
            left join fetch r.previousRequest previousRequest
            where r.id = :requestId
            """)
    Optional<MentorVerificationRequest> findByIdForUpdate(@Param("requestId") UUID requestId);

    @Query(value = """
            select r
            from MentorVerificationRequest r
            where (:status is null or r.status = :status)
              and r.submittedAt >= coalesce(:submittedFrom, r.submittedAt)
              and r.submittedAt <= coalesce(:submittedTo, r.submittedAt)
            """,
            countQuery = """
            select count(r.id)
            from MentorVerificationRequest r
            where (:status is null or r.status = :status)
              and r.submittedAt >= coalesce(:submittedFrom, r.submittedAt)
              and r.submittedAt <= coalesce(:submittedTo, r.submittedAt)
            """)
    Page<MentorVerificationRequest> findAdminQueue(
            @Param("status") VerificationStatus status,
            @Param("submittedFrom") LocalDateTime submittedFrom,
            @Param("submittedTo") LocalDateTime submittedTo,
            Pageable pageable
    );
}
