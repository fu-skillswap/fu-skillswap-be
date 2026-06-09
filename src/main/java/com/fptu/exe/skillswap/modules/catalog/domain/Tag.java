package com.fptu.exe.skillswap.modules.catalog.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tags", uniqueConstraints = {
    @UniqueConstraint(name = "uq_tags_code", columnNames = {"code"})
}, indexes = {
    @Index(name = "idx_tags_code", columnList = "code", unique = true),
    @Index(name = "idx_tags_type", columnList = "type"),
    @Index(name = "idx_tags_status", columnList = "status"),
    @Index(name = "idx_tags_parent_id", columnList = "parent_tag_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 150)
    private String nameVi;

    @Column(name = "name_en", length = 150)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TagStatus status = TagStatus.ACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private Integer weight = 10;

    @Column(name = "is_fixed", nullable = false)
    @Builder.Default
    private boolean isFixed = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_tag_id", foreignKey = @ForeignKey(name = "fk_tags_parent"))
    private Tag parentTag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_tags_creator"))
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", foreignKey = @ForeignKey(name = "fk_tags_approver"))
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

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
