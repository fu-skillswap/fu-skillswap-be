package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityTemplateRepository extends JpaRepository<AvailabilityTemplate, UUID> {

    @Query("""
            select distinct template from AvailabilityTemplate template
            left join fetch template.serviceIds
            where template.mentorUserId = :mentorUserId
            order by template.effectiveFrom desc, template.id desc
            """)
    List<AvailabilityTemplate> findOwnedWithServices(@Param("mentorUserId") UUID mentorUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct template from AvailabilityTemplate template
            left join fetch template.serviceIds
            where template.id = :templateId and template.mentorUserId = :mentorUserId
            """)
    Optional<AvailabilityTemplate> findOwnedForUpdate(@Param("templateId") UUID templateId,
                                                       @Param("mentorUserId") UUID mentorUserId);

    @Query("""
            select distinct template from AvailabilityTemplate template
            left join fetch template.serviceIds
            where template.id = :templateId
            """)
    Optional<AvailabilityTemplate> findWithServicesById(@Param("templateId") UUID templateId);

    @Query("""
            select template from AvailabilityTemplate template
            where template.mentorUserId = :mentorUserId
            order by template.id asc
            """)
    List<AvailabilityTemplate> findAllByMentorForOverlap(@Param("mentorUserId") UUID mentorUserId);
}
