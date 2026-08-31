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
@Table(name = "google_calendar_event_links", indexes = {
        @Index(name = "idx_google_calendar_event_links_status", columnList = "event_status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCalendarEventLink {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "mentor_user_id", nullable = false)
    private UUID mentorUserId;

    @Column(name = "google_event_id", nullable = false, length = 255)
    private String googleEventId;

    @Column(name = "google_meet_url", columnDefinition = "TEXT")
    private String googleMeetUrl;

    @Column(name = "etag", length = 255)
    private String etag;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 50)
    private GoogleCalendarEventStatus eventStatus;

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

    @PrePersist
    protected void onCreate() {
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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAtUtc = DateTimeUtil.instantNow();
        updatedAt = BusinessTime.fromInstant(updatedAtUtc);
    }
}
