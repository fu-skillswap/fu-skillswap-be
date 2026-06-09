package com.fptu.exe.skillswap.modules.matching.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_recommendation_logs", indexes = {
    @Index(name = "idx_rec_logs_mentee_id", columnList = "mentee_user_id"),
    @Index(name = "idx_rec_logs_mentor_id", columnList = "mentor_user_id"),
    @Index(name = "idx_rec_logs_score", columnList = "score"),
    @Index(name = "idx_rec_logs_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorRecommendationLog {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentee_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rec_logs_mentee"))
    private User mentee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rec_logs_mentor"))
    private MentorProfile mentorProfile;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "reason_text", columnDefinition = "TEXT")
    private String reasonText;

    @Column(name = "matched_tag_count", nullable = false)
    @Builder.Default
    private Integer matchedTagCount = 0;

    @Column(name = "same_major_bonus", nullable = false)
    @Builder.Default
    private Integer sameMajorBonus = 0;

    @Column(name = "same_campus_bonus", nullable = false)
    @Builder.Default
    private Integer sameCampusBonus = 0;

    @Column(name = "rating_bonus", nullable = false)
    @Builder.Default
    private Integer ratingBonus = 0;

    @Column(name = "availability_bonus", nullable = false)
    @Builder.Default
    private Integer availabilityBonus = 0;

    @Column(name = "algorithm_version", nullable = false, length = 50)
    @Builder.Default
    private String algorithmVersion = "RULE_V1";

    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    private String inputSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
