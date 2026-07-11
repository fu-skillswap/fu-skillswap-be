package com.fptu.exe.skillswap.shared.outbox;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "domain_event_outbox", indexes = {
        @Index(name = "idx_domain_event_outbox_status_available_created", columnList = "status, available_at, created_at"),
        @Index(name = "idx_domain_event_outbox_aggregate_created", columnList = "aggregate_type, aggregate_id, created_at"),
        @Index(name = "idx_domain_event_outbox_event_type_created", columnList = "event_type, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventOutbox {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 120)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private DomainEventOutboxStatus status = DomainEventOutboxStatus.PENDING;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = DateTimeUtil.now();
        createdAt = now;
        updatedAt = now;
        if (availableAt == null) {
            availableAt = now;
        }
        if (status == null) {
            status = DomainEventOutboxStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
