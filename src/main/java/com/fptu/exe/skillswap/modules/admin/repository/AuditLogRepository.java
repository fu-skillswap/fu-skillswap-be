package com.fptu.exe.skillswap.modules.admin.repository;

import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.domain.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @EntityGraph(attributePaths = {"actor"})
    @Query(value = """
            select auditLog
            from AuditLog auditLog
            left join auditLog.actor actor
            where (:actorUserId is null or actor.id = :actorUserId)
              and (:entityType is null or lower(auditLog.entityType) = lower(:entityType))
              and (:entityId is null or auditLog.entityId = :entityId)
              and (:action is null or auditLog.action = :action)
              and (:fromTime is null or auditLog.createdAt >= :fromTime)
              and (:toTime is null or auditLog.createdAt <= :toTime)
            """, countQuery = """
            select count(auditLog.id)
            from AuditLog auditLog
            left join auditLog.actor actor
            where (:actorUserId is null or actor.id = :actorUserId)
              and (:entityType is null or lower(auditLog.entityType) = lower(:entityType))
              and (:entityId is null or auditLog.entityId = :entityId)
              and (:action is null or auditLog.action = :action)
              and (:fromTime is null or auditLog.createdAt >= :fromTime)
              and (:toTime is null or auditLog.createdAt <= :toTime)
            """)
    Page<AuditLog> searchForAdmin(
            @Param("actorUserId") UUID actorUserId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("action") AuditAction action,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"actor"})
    List<AuditLog> findByEntityTypeIgnoreCaseAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
}
