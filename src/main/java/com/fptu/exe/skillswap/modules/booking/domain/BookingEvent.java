package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.booking.service.BookingTime;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEvent {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private BookingEventType eventType;

    @Column(name = "event_version", nullable = false)
    @Builder.Default
    private Integer eventVersion = 1;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private BookingEventActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 50)
    private BookingStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 50)
    private BookingStatus newStatus;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "metadata_schema_version", nullable = false)
    @Builder.Default
    private Integer metadataSchemaVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_at_utc")
    private Instant createdAtUtc;

    @PrePersist
    public void onCreate() {
        if (createdAtUtc == null && createdAt == null) {
            createdAtUtc = com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow();
            createdAt = BookingTime.fromInstant(createdAtUtc);
        } else if (createdAtUtc == null) {
            createdAtUtc = BookingTime.toInstant(createdAt);
        } else if (createdAt == null) {
            createdAt = BookingTime.fromInstant(createdAtUtc);
        }
    }
}
