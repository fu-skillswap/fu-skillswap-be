package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_verification_upload_intents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorVerificationUploadIntent {
    @Id @GeneratedUuidV7 private UUID id;
    @Column(name = "owner_user_id", nullable = false) private UUID ownerUserId;
    @Column(name = "storage_key", nullable = false, unique = true, length = 512) private String storageKey;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "expected_content_type", nullable = false, length = 100) private String expectedContentType;
    @Column(name = "expected_size_bytes", nullable = false) private long expectedSizeBytes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) @Builder.Default private MentorVerificationUploadIntentStatus status = MentorVerificationUploadIntentStatus.PENDING_UPLOAD;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "confirmed_stored_file_id", unique = true) private UUID confirmedStoredFileId;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Version @Builder.Default private Integer version = 0;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = DateTimeUtil.now(); }
}
