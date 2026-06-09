package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_verification_requests", indexes = {
    @Index(name = "idx_mentor_verification_mentor_id", columnList = "mentor_user_id"),
    @Index(name = "idx_mentor_verification_status", columnList = "status"),
    @Index(name = "idx_mentor_verification_method", columnList = "method")
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
    private VerificationStatus status = VerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_file_id", foreignKey = @ForeignKey(name = "fk_mentor_verification_file"))
    private StoredFile document;

    @Column(name = "submitted_note", columnDefinition = "TEXT")
    private String submittedNote;

    @Column(name = "ocr_raw_result", columnDefinition = "jsonb")
    private String ocrRawResult;

    @Column(name = "ocr_confidence", precision = 5, scale = 2)
    private BigDecimal ocrConfidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocr_reviewed_by", foreignKey = @ForeignKey(name = "fk_mentor_verification_ocr_reviewer"))
    private User ocrReviewedBy;

    @Column(name = "ocr_reviewed_at")
    private LocalDateTime ocrReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", foreignKey = @ForeignKey(name = "fk_mentor_verification_reviewer"))
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

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
