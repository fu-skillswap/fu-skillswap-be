package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "mentor_service_resources")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MentorServiceResource {
    @Id @GeneratedUuidV7 private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_id") private MentorService service;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 20) private MentorServiceResourceType resourceType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MentorServiceResourceVisibility visibility;
    @Column(name = "storage_key", nullable = false, unique = true, length = 512) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 120) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Version @Builder.Default private Integer version = 0;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void create(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
