package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumReportRepository extends JpaRepository<ForumReport, UUID> {

    boolean existsByReporterUserIdAndTargetTypeAndTargetId(UUID reporterUserId, ForumReportTargetType targetType, UUID targetId);

    @EntityGraph(attributePaths = {"reporterUser"})
    @Query("""
            select r
            from ForumReport r
            join r.reporterUser u
            where (:status is null or r.status = :status)
              and (:targetType is null or r.targetType = :targetType)
              and (
                    :keywordPattern is null
                    or lower(coalesce(r.description, '')) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern
              )
            """)
    Page<ForumReport> searchReports(@Param("status") ForumReportStatus status,
                                    @Param("targetType") ForumReportTargetType targetType,
                                    @Param("keywordPattern") String keywordPattern,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"reporterUser"})
    Optional<ForumReport> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ForumReport r where r.id = :id")
    Optional<ForumReport> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(r.id) from ForumReport r where r.reporterUser.id = :reporterUserId")
    long countCreatedByReporterUserId(@Param("reporterUserId") UUID reporterUserId);
}
