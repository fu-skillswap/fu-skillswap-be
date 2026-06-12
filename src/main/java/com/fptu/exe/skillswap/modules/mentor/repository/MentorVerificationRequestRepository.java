package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorVerificationRequestRepository extends JpaRepository<MentorVerificationRequest, UUID> {

    @EntityGraph(attributePaths = {"mentor", "reviewedBy", "previousRequest"})
    Optional<MentorVerificationRequest> findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(
            UUID mentorUserId,
            Collection<VerificationStatus> statuses
    );
}
