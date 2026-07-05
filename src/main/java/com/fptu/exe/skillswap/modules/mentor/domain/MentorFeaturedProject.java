package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_featured_projects", indexes = {
        @Index(name = "idx_mfp_mentor", columnList = "mentor_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorFeaturedProject {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mfp_mentor"))
    private MentorProfile mentorProfile;

    @Column(nullable = false, length = 200)
    private String title;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picture_file_id", foreignKey = @ForeignKey(name = "fk_mfp_picture"))
    private StoredFile pictureFile;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "project_description", columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "live_demo_url", columnDefinition = "TEXT")
    private String liveDemoUrl;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
