package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_verification_requests", indexes = {
    @Index(name = "idx_mentor_verification_mentor_id", columnList = "mentor_user_id"),
    @Index(name = "idx_mentor_verification_status", columnList = "status"),
    @Index(name = "idx_mentor_verification_method", columnList = "method"),
    @Index(name = "idx_mentor_verification_status_submitted_at", columnList = "status, submitted_at"),
    @Index(name = "idx_mentor_verification_status_submitted_at_id", columnList = "status, submitted_at, id"),
    @Index(name = "idx_mentor_verification_mentor_status", columnList = "mentor_user_id, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorVerificationRequest {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentor_verification_mentor"))
    private User mentor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VerificationMethod method = VerificationMethod.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.DRAFT;

    @Column(name = "submitted_note", columnDefinition = "TEXT")
    private String submittedNote;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "revision_count", nullable = false)
    @Builder.Default
    private Integer revisionCount = 0;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "terms_version", length = 80)
    private String termsVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", foreignKey = @ForeignKey(name = "fk_mentor_verification_reviewer"))
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by", foreignKey = @ForeignKey(name = "fk_mentor_verification_locked_by"))
    private User lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "lock_expires_at")
    private LocalDateTime lockExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_request_id", foreignKey = @ForeignKey(name = "fk_mentor_verification_previous"))
    private MentorVerificationRequest previousRequest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
