package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityMentorMutationLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AvailabilityMentorMutationLockRepository extends JpaRepository<AvailabilityMentorMutationLock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from AvailabilityMentorMutationLock lock where lock.mentorUserId = :mentorUserId")
    Optional<AvailabilityMentorMutationLock> findByMentorUserIdForUpdate(@Param("mentorUserId") UUID mentorUserId);
}
