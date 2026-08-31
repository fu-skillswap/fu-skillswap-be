package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;

@Repository
public interface AvailabilitySlotServiceRepository extends JpaRepository<AvailabilitySlotService, AvailabilitySlotServiceId> {

    List<AvailabilitySlotService> findBySlotIdOrderByCreatedAtAsc(UUID slotId);

    @Query("""
            select slotService
            from AvailabilitySlotService slotService
            where slotService.slot.id = :slotId
              and slotService.id.serviceId = :serviceId
            """)
    Optional<AvailabilitySlotService> findBySlotIdAndServiceId(
            @Param("slotId") UUID slotId,
            @Param("serviceId") UUID serviceId
    );

    @Query("""
            select slotService
            from AvailabilitySlotService slotService
            where slotService.slot.id in :slotIds
            order by slotService.createdAt asc
            """)
    List<AvailabilitySlotService> findBySlotIdInOrderByCreatedAtAsc(@Param("slotIds") Collection<UUID> slotIds);

    @Query("""
            select count(slotService.id) > 0
            from AvailabilitySlotService slotService
            where slotService.slot.id = :slotId
              and slotService.id.serviceId = :serviceId
            """)
    boolean existsBySlotIdAndServiceId(
            @Param("slotId") UUID slotId,
            @Param("serviceId") UUID serviceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select slotService
            from AvailabilitySlotService slotService
            join fetch slotService.slot slot
            where slotService.id.serviceId = :serviceId
              and slot.isActive = true
              and slot.endTime > :now
            order by slot.id asc
            """)
    List<AvailabilitySlotService> findFutureActiveBindingsByServiceIdForUpdate(
            @Param("serviceId") UUID serviceId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select distinct slotService.slot.mentorUserId
            from AvailabilitySlotService slotService
            where slotService.slot.mentorUserId in :mentorUserIds
              and slotService.slot.isActive = true
              and slotService.slot.startTime >= :now
            """)
    List<UUID> findMentorUserIdsWithFutureActiveSlots(
            @Param("mentorUserIds") Collection<UUID> mentorUserIds,
            @Param("now") LocalDateTime now
    );

    @Modifying
    void deleteBySlotId(UUID slotId);
}
