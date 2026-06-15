package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
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
public interface MentorAvailabilitySlotRepository extends JpaRepository<MentorAvailabilitySlot, UUID> {

    List<MentorAvailabilitySlot> findByMentorProfileUserIdAndIsActiveTrueAndIsBookedFalseAndStartTimeAfterOrderByStartTimeAsc(
            UUID mentorUserId,
            LocalDateTime startTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slot
            from MentorAvailabilitySlot slot
            join fetch slot.mentorProfile mp
            join fetch mp.user u
            where slot.id = :slotId
            """)
    Optional<MentorAvailabilitySlot> findByIdForUpdate(@Param("slotId") UUID slotId);
}
