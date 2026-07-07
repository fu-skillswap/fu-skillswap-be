package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface AvailabilitySlotServiceRepository extends JpaRepository<AvailabilitySlotService, AvailabilitySlotServiceId> {

    @EntityGraph(attributePaths = {"service", "service.helpTopics"})
    List<AvailabilitySlotService> findBySlotIdOrderByCreatedAtAsc(UUID slotId);

    @EntityGraph(attributePaths = {"service", "service.helpTopics", "slot", "slot.mentorProfile"})
    @Query("""
            select slotService
            from AvailabilitySlotService slotService
            where slotService.slot.id = :slotId
              and slotService.service.id = :serviceId
            """)
    java.util.Optional<AvailabilitySlotService> findBySlotIdAndServiceId(
            @Param("slotId") UUID slotId,
            @Param("serviceId") UUID serviceId
    );

    @EntityGraph(attributePaths = {"service", "service.helpTopics"})
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
              and slotService.service.id = :serviceId
            """)
    boolean existsBySlotIdAndServiceId(
            @Param("slotId") UUID slotId,
            @Param("serviceId") UUID serviceId
    );

    @Query("""
            select distinct slotService.slot.mentorProfile.userId
            from AvailabilitySlotService slotService
            where slotService.slot.mentorProfile.userId in :mentorUserIds
              and slotService.slot.isActive = true
              and slotService.slot.startTime >= :now
              and slotService.service.isActive = true
              and slotService.service.durationMinutes = :durationMinutes
            """)
    List<UUID> findMentorUserIdsWithFutureActiveSlotServiceDuration(
            @Param("mentorUserIds") Collection<UUID> mentorUserIds,
            @Param("durationMinutes") Integer durationMinutes,
            @Param("now") LocalDateTime now
    );

    @Modifying
    void deleteBySlotId(UUID slotId);
}
