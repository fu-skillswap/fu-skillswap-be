package com.fptu.exe.skillswap.modules.academic.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_profiles", uniqueConstraints = {
    @UniqueConstraint(name = "uq_student_profiles_code", columnNames = {"student_code"})
}, indexes = {
    @Index(name = "idx_student_profiles_campus_id", columnList = "campus_id"),
    @Index(name = "idx_student_profiles_program_id", columnList = "program_id"),
    @Index(name = "idx_student_profiles_spec_id", columnList = "specialization_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_student_profiles_user"))
    private User user;

    @Column(name = "student_code", length = 30)
    private String studentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id", foreignKey = @ForeignKey(name = "fk_student_profiles_campus"))
    private Campus campus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", foreignKey = @ForeignKey(name = "fk_student_profiles_program"))
    private AcademicProgram program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialization_id", foreignKey = @ForeignKey(name = "fk_student_profiles_spec"))
    private Specialization specialization;

    private Integer semester;

    @Column(name = "intake_year")
    private Integer intakeYear;

    @Column(name = "is_alumni", nullable = false)
    @Builder.Default
    private boolean isAlumni = false;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(columnDefinition = "TEXT")
    private String bio;

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
