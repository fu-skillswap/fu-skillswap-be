package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.Campaign;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID>, JpaSpecificationExecutor<Campaign> {

    List<Campaign> findByStatus(CampaignStatus status);

    long countByStatus(CampaignStatus status);

    @Query("""
            select c from Campaign c
            where c.status = :status
              and c.startAt is not null
              and c.startAt <= :now
            """)
    List<Campaign> findScheduledReadyToActivate(@Param("status") CampaignStatus status, @Param("now") LocalDateTime now);

    @Query("""
            select c from Campaign c
            where c.status = :status
              and c.endAt is not null
              and c.endAt <= :now
            """)
    List<Campaign> findActiveExpired(@Param("status") CampaignStatus status, @Param("now") LocalDateTime now);

    @Query("""
            select campaign.id
            from Campaign campaign
            where campaign.status = :status
            order by campaign.id asc
            """)
    List<UUID> findIdsByStatusOrderByIdAsc(@Param("status") CampaignStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from Campaign campaign where campaign.id = :id")
    Optional<Campaign> findByIdForUpdate(@Param("id") UUID id);
}
