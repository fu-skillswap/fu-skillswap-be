package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_profiles", indexes = {
    @Index(name = "idx_mentor_profiles_status", columnList = "status"),
    @Index(name = "idx_mentor_profiles_avg_rating", columnList = "average_rating"),
    @Index(name = "idx_mentor_profiles_available", columnList = "is_available"),
    @Index(name = "idx_mentor_profiles_teaching_mode", columnList = "teaching_mode")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentor_profiles_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MentorStatus status = MentorStatus.DRAFT;

    @Column(length = 200)
    private String headline;

    @Column(name = "expertise_description", columnDefinition = "TEXT")
    private String expertiseDescription;

    @Column(name = "supporting_subjects", columnDefinition = "TEXT")
    private String supportingSubjects;

    @Enumerated(EnumType.STRING)
    @Column(name = "teaching_mode", length = 20)
    private TeachingMode teachingMode;

    @Column(name = "session_duration", nullable = false)
    @Builder.Default
    private Integer sessionDuration = 60;

    @Column(name = "portfolio_url", columnDefinition = "TEXT")
    private String portfolioUrl;

    @Column(name = "linkedin_url", columnDefinition = "TEXT")
    private String linkedinUrl;

    @Column(name = "github_url", columnDefinition = "TEXT")
    private String githubUrl;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews", nullable = false)
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(name = "total_sessions", nullable = false)
    @Builder.Default
    private Integer totalSessions = 0;

    @Column(name = "total_completed_sessions", nullable = false)
    @Builder.Default
    private Integer totalCompletedSessions = 0;

    @Column(name = "total_rejected_bookings", nullable = false)
    @Builder.Default
    private Integer totalRejectedBookings = 0;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private boolean isAvailable = true;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by", foreignKey = @ForeignKey(name = "fk_mentor_profiles_verifier"))
    private User verifiedBy;

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
