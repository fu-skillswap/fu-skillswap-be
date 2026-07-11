package com.fptu.exe.skillswap.modules.forum.domain;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "forum_posts", indexes = {
        @Index(name = "idx_forum_posts_status_last_activity_id", columnList = "status, last_activity_at DESC, id"),
        @Index(name = "idx_forum_posts_status_created", columnList = "status, created_at"),
        @Index(name = "idx_forum_posts_help_topic_created", columnList = "help_topic_id, created_at"),
        @Index(name = "idx_forum_posts_author_created", columnList = "author_user_id, created_at")
})
@SQLDelete(sql = "UPDATE forum_posts SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumPost {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_forum_posts_author"))
    private User authorUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "help_topic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_forum_posts_help_topic"))
    private Tag helpTopic;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ForumPostStatus status = ForumPostStatus.PUBLISHED;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "reaction_count", nullable = false)
    @Builder.Default
    private Integer reactionCount = 0;

    @Column(name = "report_count", nullable = false)
    @Builder.Default
    private Integer reportCount = 0;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hidden_by_user_id")
    private UUID hiddenByUserId;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "image_urls", columnDefinition = "jsonb")
    @Builder.Default
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();


    @PrePersist
    protected void onCreate() {
        LocalDateTime now = DateTimeUtil.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lastActivityAt == null) {
            lastActivityAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
