package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_verification_documents", indexes = {
        @Index(name = "idx_mentor_verification_documents_request_id", columnList = "request_id"),
        @Index(name = "idx_mentor_verification_documents_request_type", columnList = "request_id, document_type"),
        @Index(name = "idx_mentor_verification_documents_request_type_active", columnList = "request_id, document_type, is_active")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorVerificationDocument {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentor_verification_documents_request"))
    private MentorVerificationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private VerificationDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VerificationDocumentStatus status = VerificationDocumentStatus.UPLOADED;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_kind", nullable = false, length = 20)
    private VerificationStorageKind storageKind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentor_verification_documents_file"))
    private StoredFile storedFile;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentor_verification_documents_uploaded_by"))
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}




