package com.fptu.exe.skillswap.modules.system.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "internal_telemetry_events", indexes = {
        @Index(name = "idx_internal_telemetry_event_type_created", columnList = "event_type, created_at"),
        @Index(name = "idx_internal_telemetry_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_internal_telemetry_subject", columnList = "subject_type, subject_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTelemetryEvent {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "subject_type", length = 80)
    private String subjectType;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimeUtil.now();
        }
    }
}
