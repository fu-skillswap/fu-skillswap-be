package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_service_resource_upload_intents", indexes = {
    @Index(name = "idx_mentor_service_resource_intent_service", columnList = "service_id"),
    @Index(name = "idx_mentor_service_resource_intent_expiry", columnList = "status, expires_at")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MentorServiceResourceUploadIntent {
    public enum Status { PENDING_UPLOAD, CONFIRMED, EXPIRED, REJECTED }
    @Id @GeneratedUuidV7 private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_id") private MentorService service;
    @Column(name = "storage_key", nullable = false, unique = true, length = 512) private String storageKey;
    @Enumerated(EnumType.STRING) @Column(name = "expected_type", nullable = false) private MentorServiceResourceType expectedType;
    @Enumerated(EnumType.STRING) @Column(nullable = false) @Builder.Default private Status status=Status.PENDING_UPLOAD;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "cleanup_lease_until") private LocalDateTime cleanupLeaseUntil;
    @Column(name = "next_cleanup_at") private LocalDateTime nextCleanupAt;
    @Builder.Default @Column(name = "cleanup_attempt_count", nullable = false) private int cleanupAttemptCount = 0;
    @Column(name = "last_cleanup_error", columnDefinition = "TEXT") private String lastCleanupError;
    @Column(name = "storage_deleted_at") private LocalDateTime storageDeletedAt;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resource_id") private MentorServiceResource resource;
    @Version @Builder.Default private Integer version=0;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void create(){createdAt=LocalDateTime.now();}
}
