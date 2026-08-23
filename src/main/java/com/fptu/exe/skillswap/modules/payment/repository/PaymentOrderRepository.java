package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByTargetTypeAndTargetId(PaymentTargetType targetType, UUID targetId);

    List<PaymentOrder> findByTargetTypeAndTargetIdIn(PaymentTargetType targetType, Collection<UUID> targetIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PaymentOrder po where po.targetType = :targetType and po.targetId = :targetId")
    Optional<PaymentOrder> findByTargetTypeAndTargetIdForUpdate(@Param("targetType") PaymentTargetType targetType, @Param("targetId") UUID targetId);

    Optional<PaymentOrder> findByOrderCode(String orderCode);

    Optional<PaymentOrder> findByProviderOrderCode(String providerOrderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PaymentOrder po where po.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByTargetTypeAndTargetId(PaymentTargetType targetType, UUID targetId);

    boolean existsByProviderOrderCode(String providerOrderCode);

    boolean existsByTargetTypeAndTargetIdAndStatus(PaymentTargetType targetType, UUID targetId, PaymentOrderStatus status);

    boolean existsByProviderEventId(String providerEventId);

    long countByPayerUserId(UUID payerUserId);

    @Query("""
            select coalesce(sum(po.campaignCreditScoin), 0)
            from PaymentOrder po
            where po.campaignId = :campaignId
              and po.status not in :excludedStatuses
            """)
    Integer sumCampaignCreditByCampaignIdAndStatusNotIn(
            @Param("campaignId") UUID campaignId,
            @Param("excludedStatuses") Collection<PaymentOrderStatus> excludedStatuses
    );

    long countByCampaignIdAndStatusNotIn(UUID campaignId, Collection<PaymentOrderStatus> excludedStatuses);

    @Query("""
            select coalesce(sum(po.grossScoin), 0)
            from PaymentOrder po
            where po.campaignId = :campaignId
              and po.status not in :excludedStatuses
            """)
    Integer sumTotalScoinByCampaignIdAndStatusNotIn(
            @Param("campaignId") UUID campaignId,
            @Param("excludedStatuses") Collection<PaymentOrderStatus> excludedStatuses
    );

    @Query("""
            select po
            from PaymentOrder po
            where po.status in :statuses
              and po.updatedAt <= :updatedBefore
              and po.providerOrderCode is not null
            order by po.updatedAt asc
            """)
    List<PaymentOrder> findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            @Param("statuses") Collection<PaymentOrderStatus> statuses,
            @Param("updatedBefore") LocalDateTime updatedBefore,
            org.springframework.data.domain.Pageable pageable
    );
}
