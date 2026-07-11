package com.fptu.exe.skillswap.modules.forum.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "forum_comments", indexes = {
        @Index(name = "idx_forum_comments_post_status_created_id_asc", columnList = "post_id, status, created_at ASC, id"),
        @Index(name = "idx_forum_comments_post_created", columnList = "post_id, created_at"),
        @Index(name = "idx_forum_comments_author_created", columnList = "author_user_id, created_at")
})
@SQLDelete(sql = "UPDATE forum_comments SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumComment {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_forum_comments_post"))
    private ForumPost post;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_forum_comments_author"))
    private User authorUser;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ForumCommentStatus status = ForumCommentStatus.VISIBLE;

    @Column(name = "report_count", nullable = false)
    @Builder.Default
    private Integer reportCount = 0;

    @Column(name = "reaction_count", nullable = false)
    @Builder.Default
    private Integer reactionCount = 0;

    @Column(name = "reply_to_comment_id")
    private UUID replyToCommentId;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
