package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByBookingId(UUID bookingId);

    Optional<PaymentOrder> findByOrderCode(String orderCode);

    Optional<PaymentOrder> findByProviderOrderCode(String providerOrderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PaymentOrder po where po.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PaymentOrder po where po.providerOrderCode = :providerOrderCode")
    Optional<PaymentOrder> findByProviderOrderCodeForUpdate(@Param("providerOrderCode") String providerOrderCode);

    boolean existsByBookingId(UUID bookingId);

    boolean existsByProviderOrderCode(String providerOrderCode);

    boolean existsByBookingIdAndStatus(UUID bookingId, PaymentOrderStatus status);

    boolean existsByProviderEventId(String providerEventId);

    @Query("""
            select coalesce(sum(po.campaignCreditScoin), 0)
            from PaymentOrder po
            where po.campaignId = :campaignId
              and po.status not in :excludedStatuses
            """)
    Integer sumCampaignCreditByCampaignIdAndStatusNotIn(
            @Param("campaignId") UUID campaignId,
            @Param("excludedStatuses") java.util.Collection<PaymentOrderStatus> excludedStatuses
    );
}
