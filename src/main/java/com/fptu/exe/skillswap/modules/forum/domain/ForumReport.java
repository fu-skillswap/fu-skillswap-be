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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "forum_reports", uniqueConstraints = {
        @UniqueConstraint(name = "uq_forum_reports_reporter_target", columnNames = {"reporter_user_id", "target_type", "target_id"})
}, indexes = {
        @Index(name = "idx_forum_reports_status_created", columnList = "status, created_at"),
        @Index(name = "idx_forum_reports_target", columnList = "target_type, target_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumReport {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_forum_reports_reporter"))
    private User reporterUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ForumReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 30)
    private ForumReportReasonType reasonType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private ForumReportStatus status = ForumReportStatus.OPEN;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
