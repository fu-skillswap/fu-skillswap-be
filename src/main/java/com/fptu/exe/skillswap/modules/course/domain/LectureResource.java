package com.fptu.exe.skillswap.modules.course.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lecture_resources")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureResource {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lecture_resources_lecture"))
    private CourseLecture lecture;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private MaterialType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider_type", nullable = false, length = 32)
    private StorageProviderType storageProviderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaterialStatus status;

    // Bunny.net specific fields
    @Column(name = "bunny_video_id", length = 64)
    private String bunnyVideoId;

    @Column(name = "bunny_library_id", length = 64)
    private String bunnyLibraryId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // Document & External links
    @Column(name = "document_object_key", length = 255)
    private String documentObjectKey;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
