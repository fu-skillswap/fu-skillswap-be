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
@Table(name = "course_materials", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_materials_chapter_sort", columnNames = {"chapter_id", "sort_order"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseMaterial {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_course_materials_chapter"))
    private CourseChapter chapter;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 16)
    private CourseMaterialType materialType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_previewable", nullable = false)
    @Builder.Default
    private boolean isPreviewable = false;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private boolean isPublished = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider_type", nullable = false, length = 32)
    private StorageProviderType storageProviderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaterialStatus status;

    @Column(name = "bunny_video_id", length = 64)
    private String bunnyVideoId;

    @Column(name = "bunny_library_id", length = 64)
    private String bunnyLibraryId;

    /** Provider-neutral R2 object reference for the future video upload flow. */
    @Column(name = "video_object_key", length = 500)
    private String videoObjectKey;

    /** MIME type verified from storage when the future video upload is confirmed. */
    @Column(name = "video_content_type", length = 100)
    private String videoContentType;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "document_object_key", length = 255)
    private String documentObjectKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "upload_expires_at")
    private Instant uploadExpiresAt;

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
