package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorServiceRepository extends JpaRepository<MentorService, UUID> {

    List<MentorService> findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID mentorUserId);

    boolean existsByMentorProfileUserIdAndIsActiveTrue(UUID mentorUserId);

    boolean existsByMentorProfileUserIdAndIsActiveTrueAndDeliveryMode(
            UUID mentorUserId,
            MentorServiceDeliveryMode deliveryMode
    );

    List<MentorService> findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(UUID mentorUserId, boolean isActive);

    List<MentorService> findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(List<UUID> mentorUserIds);

    List<MentorService> findByMentorProfileUserIdOrderByCreatedAtAsc(UUID mentorUserId);

    Optional<MentorService> findByIdAndMentorProfileUserId(UUID id, UUID mentorUserId);

    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select service
            from MentorService service
            join fetch service.mentorProfile
            where service.id = :serviceId
              and service.mentorProfile.userId = :mentorUserId
            """)
    Optional<MentorService> findByIdAndMentorProfileUserIdForUpdate(
            @Param("serviceId") UUID serviceId,
            @Param("mentorUserId") UUID mentorUserId
    );

    Optional<MentorService> findByIdAndMentorProfileUserIdAndIsActiveTrue(UUID id, UUID mentorUserId);

    @Query("""
            select distinct service
            from MentorService service
            join fetch service.mentorProfile mentorProfile
            join fetch mentorProfile.user mentorUser
            where service.id = :serviceId
            """)
    Optional<MentorService> findByIdForPricingPreview(@Param("serviceId") UUID serviceId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s.title FROM MentorService s WHERE s.isActive = true")
    List<String> findAllActiveServiceTitles();
}
