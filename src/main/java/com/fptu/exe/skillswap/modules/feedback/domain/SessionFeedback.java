package com.fptu.exe.skillswap.modules.feedback.domain;

import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_feedbacks", uniqueConstraints = {
    @UniqueConstraint(name = "uq_session_feedbacks", columnNames = {"session_id", "reviewer_user_id"})
}, indexes = {
    @Index(name = "idx_feedbacks_reviewee_id", columnList = "reviewee_user_id"),
    @Index(name = "idx_feedbacks_rating", columnList = "rating")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionFeedback {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_feedbacks_session"))
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_feedbacks_reviewer"))
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_feedbacks_reviewee"))
    private User reviewee;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "satisfaction_level")
    private Integer satisfactionLevel;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "would_recommend")
    private Boolean wouldRecommend;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = true;

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
