package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorVerificationRequestRepository extends JpaRepository<MentorVerificationRequest, UUID> {

    @Override
    @EntityGraph(attributePaths = {"mentor", "reviewedBy", "previousRequest"})
    Optional<MentorVerificationRequest> findById(UUID id);

    @EntityGraph(attributePaths = {"mentor", "reviewedBy", "previousRequest"})
    Optional<MentorVerificationRequest> findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(
            UUID mentorUserId,
            Collection<VerificationStatus> statuses
    );

    @EntityGraph(attributePaths = {"mentor", "reviewedBy", "previousRequest"})
    Optional<MentorVerificationRequest> findFirstByMentorIdOrderByCreatedAtDesc(UUID mentorUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from MentorVerificationRequest r
            join fetch r.mentor mentor
            left join fetch r.reviewedBy reviewer
            left join fetch r.previousRequest previousRequest
            where r.id = :requestId
            """)
    Optional<MentorVerificationRequest> findByIdForUpdate(@Param("requestId") UUID requestId);

    @Query(value = """
            select
                r.id as requestId,
                mentor.id as mentorUserId,
                mentor.email as mentorEmail,
                mentor.fullName as mentorFullName,
                mentor.avatarUrl as mentorAvatarUrl,
                r.status as status,
                r.revisionCount as revisionCount,
                r.submittedAt as submittedAt,
                r.createdAt as createdAt,
                r.updatedAt as updatedAt
            from MentorVerificationRequest r
            join r.mentor mentor
            where (:status is null or r.status = :status)
              and (:submittedFrom is null or r.submittedAt >= :submittedFrom)
              and (:submittedTo is null or r.submittedAt <= :submittedTo)
              and (
                    :keywordPattern is null
                    or lower(mentor.email) like :keywordPattern
                    or lower(mentor.fullName) like :keywordPattern
                    or lower(function('translate', mentor.email, 'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy')) like :normalizedKeywordPattern
                    or lower(function('translate', mentor.fullName, 'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy')) like :normalizedKeywordPattern
                  )
            """,
            countQuery = """
            select count(r.id)
            from MentorVerificationRequest r
            join r.mentor mentor
            where (:status is null or r.status = :status)
              and (:submittedFrom is null or r.submittedAt >= :submittedFrom)
              and (:submittedTo is null or r.submittedAt <= :submittedTo)
              and (
                    :keywordPattern is null
                    or lower(mentor.email) like :keywordPattern
                    or lower(mentor.fullName) like :keywordPattern
                    or lower(function('translate', mentor.email, 'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy')) like :normalizedKeywordPattern
                    or lower(function('translate', mentor.fullName, 'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ', 'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy')) like :normalizedKeywordPattern
                  )
            """)
    Page<AdminMentorVerificationQueueProjection> searchAdminQueue(
            @Param("status") VerificationStatus status,
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("keywordPattern") String keywordPattern,
            @Param("normalizedKeywordPattern") String normalizedKeywordPattern,
            @Param("submittedFrom") LocalDateTime submittedFrom,
            @Param("submittedTo") LocalDateTime submittedTo,
            Pageable pageable
    );

    @Query(value = """
            select
                r.id as requestId,
                mentor.id as mentorUserId,
                mentor.email as mentorEmail,
                mentor.fullName as mentorFullName,
                mentor.avatarUrl as mentorAvatarUrl,
                r.status as status,
                r.revisionCount as revisionCount,
                r.submittedAt as submittedAt,
                r.createdAt as createdAt,
                r.updatedAt as updatedAt
            from MentorVerificationRequest r
            join r.mentor mentor
            where (:status is null or r.status = :status)
              and (:submittedFrom is null or r.submittedAt >= :submittedFrom)
              and (:submittedTo is null or r.submittedAt <= :submittedTo)
            """,
            countQuery = """
            select count(r.id)
            from MentorVerificationRequest r
            join r.mentor mentor
            where (:status is null or r.status = :status)
              and (:submittedFrom is null or r.submittedAt >= :submittedFrom)
              and (:submittedTo is null or r.submittedAt <= :submittedTo)
            """)
    Page<AdminMentorVerificationQueueProjection> findAdminQueueWithoutKeyword(
            @Param("status") VerificationStatus status,
            @Param("submittedFrom") LocalDateTime submittedFrom,
            @Param("submittedTo") LocalDateTime submittedTo,
            Pageable pageable
    );
}
