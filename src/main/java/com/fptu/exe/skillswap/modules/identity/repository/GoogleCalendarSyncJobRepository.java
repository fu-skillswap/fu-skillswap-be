package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarSyncJob;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarSyncJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoogleCalendarSyncJobRepository extends JpaRepository<GoogleCalendarSyncJob, UUID> {

    Optional<GoogleCalendarSyncJob> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j
            from GoogleCalendarSyncJob j
            where j.status in :statuses
              and (
                   (j.runAfterUtc is not null and j.runAfterUtc <= :nowUtc)
                or (j.runAfterUtc is null and j.runAfter <= :nowLegacy)
              )
            order by coalesce(j.runAfterUtc, j.createdAtUtc) asc, coalesce(j.createdAtUtc, j.runAfterUtc) asc
            """)
    List<GoogleCalendarSyncJob> findTop20RunnableForUpdateUtc(
            @Param("statuses") Collection<GoogleCalendarSyncJobStatus> statuses,
            @Param("nowUtc") Instant nowUtc,
            @Param("nowLegacy") LocalDateTime nowLegacy,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GoogleCalendarSyncJob j
            set j.status = :processingStatus,
                j.updatedAtUtc = :nowUtc,
                j.updatedAt = :nowLegacy
            where j.id = :jobId
              and j.status in :runnableStatuses
              and (
                   (j.runAfterUtc is not null and j.runAfterUtc <= :nowUtc)
                or (j.runAfterUtc is null and j.runAfter <= :nowLegacy)
              )
            """)
    int claimForProcessingUtc(
            @Param("jobId") UUID jobId,
            @Param("runnableStatuses") Collection<GoogleCalendarSyncJobStatus> runnableStatuses,
            @Param("processingStatus") GoogleCalendarSyncJobStatus processingStatus,
            @Param("nowUtc") Instant nowUtc,
            @Param("nowLegacy") LocalDateTime nowLegacy
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j
            from GoogleCalendarSyncJob j
            where j.status in :statuses
              and j.runAfter <= :now
            order by j.runAfter asc, j.createdAt asc
            """)
    List<GoogleCalendarSyncJob> findTop20RunnableForUpdate(
            @Param("statuses") Collection<GoogleCalendarSyncJobStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update GoogleCalendarSyncJob j
            set j.status = :processingStatus,
                j.updatedAt = :now
            where j.id = :jobId
              and j.status in :runnableStatuses
              and j.runAfter <= :now
            """)
    int claimForProcessing(
            @Param("jobId") UUID jobId,
            @Param("runnableStatuses") Collection<GoogleCalendarSyncJobStatus> runnableStatuses,
            @Param("processingStatus") GoogleCalendarSyncJobStatus processingStatus,
            @Param("now") LocalDateTime now
    );
}
