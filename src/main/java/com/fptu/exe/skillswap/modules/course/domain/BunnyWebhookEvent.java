package com.fptu.exe.skillswap.modules.course.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "bunny_webhook_events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_bunny_webhook_events_external_id", columnNames = {"external_event_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BunnyWebhookEvent {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "external_event_id", length = 128)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "video_id", nullable = false, length = 64)
    private String videoId;

    @Column(name = "library_id", nullable = false, length = 64)
    private String libraryId;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
