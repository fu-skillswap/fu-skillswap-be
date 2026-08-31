package com.fptu.exe.skillswap.modules.filestorage.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "public_asset_upload_intents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicAssetUploadIntent {
    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FilePurpose purpose;

    @Column(name = "object_key", nullable = false, unique = true, columnDefinition = "TEXT")
    private String objectKey;

    @Column(name = "expected_content_type", nullable = false, length = 120)
    private String expectedContentType;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_file_id")
    private StoredFile confirmedFile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = DateTimeUtil.now();
        if (version == null) version = 0;
    }
}
