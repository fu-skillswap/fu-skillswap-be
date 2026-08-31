package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.shared.time.BusinessTime;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "google_calendar_sync_jobs", indexes = {
        @Index(name = "idx_google_calendar_sync_jobs_poll", columnList = "status, run_after"),
        @Index(name = "idx_google_calendar_sync_jobs_poll_utc", columnList = "status, run_after_utc")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCalendarSyncJob {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "mentor_user_id", nullable = false)
    private UUID mentorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private GoogleCalendarSyncJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private GoogleCalendarSyncJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "run_after", nullable = false)
    private LocalDateTime runAfter;

    @Column(name = "run_after_utc")
    private Instant runAfterUtc;

    @Column(name = "idempotency_key", nullable = false, length = 200, unique = true)
    private String idempotencyKey;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_at_utc")
    private Instant completedAtUtc;

    @PrePersist
    protected void onCreate() {
        if (runAfterUtc == null) {
            runAfterUtc = runAfter != null ? BusinessTime.toInstant(runAfter) : DateTimeUtil.instantNow();
        }
        if (runAfter == null) {
            runAfter = BusinessTime.fromInstant(runAfterUtc);
        }
        if (createdAtUtc == null) {
            createdAtUtc = createdAt != null ? BusinessTime.toInstant(createdAt) : DateTimeUtil.instantNow();
        }
        if (createdAt == null) {
            createdAt = BusinessTime.fromInstant(createdAtUtc);
        }
        if (updatedAtUtc == null) {
            updatedAtUtc = updatedAt != null ? BusinessTime.toInstant(updatedAt) : createdAtUtc;
        }
        if (updatedAt == null) {
            updatedAt = BusinessTime.fromInstant(updatedAtUtc);
        }
        syncDualWriteFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAtUtc = DateTimeUtil.instantNow();
        updatedAt = BusinessTime.fromInstant(updatedAtUtc);
        syncDualWriteFields();
    }

    private void syncDualWriteFields() {
        if (runAfterUtc != null && runAfter == null) {
            runAfter = BusinessTime.fromInstant(runAfterUtc);
        } else if (runAfter != null && runAfterUtc == null) {
            runAfterUtc = BusinessTime.toInstant(runAfter);
        }

        if (completedAtUtc != null && completedAt == null) {
            completedAt = BusinessTime.fromInstant(completedAtUtc);
        } else if (completedAt != null && completedAtUtc == null) {
            completedAtUtc = BusinessTime.toInstant(completedAt);
        }
    }
}
