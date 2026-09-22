package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile {

    /** Stable profile identifier; legacy rows use the user UUID to retain references. */
    @Column(name = "id", nullable = false, updatable = false, unique = true)
    private UUID id;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_student_profiles_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", length = 30)
    private StudentProfileType profileType;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "custom_institution_name", length = 200)
    private String customInstitutionName;

    @Column(name = "custom_institution_province_id")
    private UUID customInstitutionProvinceId;

    @Column(name = "field_group_id")
    private UUID fieldGroupId;

    @Column(name = "major_name", length = 200)
    private String majorName;

    @Column(name = "enrollment_year")
    private Integer enrollmentYear;

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private boolean onboardingCompleted = false;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(columnDefinition = "TEXT")
    private String bio;

    /** Legacy profiles must be upgraded before they count as onboarded. */
    public boolean isProfileMigrationRequired() {
        return profileType == null;
    }

    /** The profile type is the sole boundary between legacy and current completion rules. */
    public boolean isOnboardingComplete() {
        return profileType != null && onboardingCompleted;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = userId;
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}




