package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.MentorPayoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MentorPayoutProfileRepository extends JpaRepository<MentorPayoutProfile, UUID> {

    List<MentorPayoutProfile> findByMentorUserIdOrderByCreatedAtDesc(UUID mentorUserId);

    Optional<MentorPayoutProfile> findByIdAndMentorUserId(UUID id, UUID mentorUserId);

    Optional<MentorPayoutProfile> findFirstByMentorUserIdAndIsDefaultTrueAndIsActiveTrue(UUID mentorUserId);

    long countByMentorUserIdAndIsDefaultTrue(UUID mentorUserId);
}
