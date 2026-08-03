package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplateReconciliation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityTemplateReconciliationRepository extends JpaRepository<AvailabilityTemplateReconciliation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reconciliation from AvailabilityTemplateReconciliation reconciliation where reconciliation.templateId = :templateId")
    Optional<AvailabilityTemplateReconciliation> findByTemplateIdForUpdate(@Param("templateId") UUID templateId);

    @Query(value = """
            select reconciliation.template_id
            from availability_template_reconciliation reconciliation
            join availability_templates template on template.id = reconciliation.template_id
            where template.configured_status = 'ACTIVE'
              and template.effective_from <= :horizonEnd
              and (template.effective_to is null or template.effective_to >= :today)
              and reconciliation.next_reconcile_at <= :now
              and (reconciliation.claimed_until is null or reconciliation.claimed_until < :now)
            order by reconciliation.next_reconcile_at asc, reconciliation.template_id asc
            for update skip locked
            limit :limit
            """, nativeQuery = true)
    List<UUID> findDueTemplateIdsForClaim(@Param("today") LocalDate today,
                                          @Param("horizonEnd") LocalDate horizonEnd,
                                          @Param("now") LocalDateTime now,
                                          @Param("limit") int limit);

    @Query("""
            select count(reconciliation) from AvailabilityTemplateReconciliation reconciliation
            join reconciliation.template template
            where template.configuredStatus = com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplateConfiguredStatus.ACTIVE
              and reconciliation.nextReconcileAt <= :now
              and (reconciliation.claimedUntil is null or reconciliation.claimedUntil < :now)
            """)
    long countDue(@Param("now") LocalDateTime now);
}
