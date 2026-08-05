package com.fptu.exe.skillswap.modules.course.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_enrollment_settlements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollmentSettlement {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_settlements_enrollment"))
    private CourseEnrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_settlements_session"))
    private CourseSession courseSession;

    @Column(name = "mentor_payout_scoin", nullable = false)
    private int mentorPayoutScoin;

    @Column(name = "platform_fee_scoin", nullable = false)
    private int platformFeeScoin;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "HELD"; // HELD, ELIGIBLE, RELEASED, REFUNDED

    @Column(name = "eligible_at")
    private Instant eligibleAt;

    @Column(name = "released_at")
    private Instant releasedAt;
}
