package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorBookingPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface MentorBookingPolicyRepository extends JpaRepository<MentorBookingPolicy, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MentorBookingPolicy p where p.mentorUserId = :mentorUserId")
    Optional<MentorBookingPolicy> findByMentorUserIdForUpdate(@Param("mentorUserId") UUID mentorUserId);

    Optional<MentorBookingPolicy> findByMentorUserId(UUID mentorUserId);
}
