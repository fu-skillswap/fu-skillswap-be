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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "course_enrollment_settlements",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_settlements_enrollment_session", columnNames = {"enrollment_id", "course_session_id"})
    }
)
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

    @Column(name = "base_price_scoin", nullable = false)
    private int basePriceScoin;

    @Column(name = "buyer_fee_scoin", nullable = false)
    private int buyerFeeScoin;

    @Column(name = "mentor_commission_scoin", nullable = false)
    private int mentorCommissionScoin;

    @Column(name = "platform_revenue_scoin", nullable = false)
    private int platformRevenueScoin;

    /** Learner voluntary refunds exclude the buyer fee by the locked policy. */
    @Column(name = "student_refundable_scoin", nullable = false)
    private int studentRefundableScoin;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private CourseSettlementStatus status = CourseSettlementStatus.HELD;

    @Column(name = "eligible_at")
    private Instant eligibleAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_reason", length = 120)
    private String refundReason;

    @Column(name = "release_operation_key", unique = true, length = 160)
    private String releaseOperationKey;

    @Column(name = "refund_operation_key", unique = true, length = 160)
    private String refundOperationKey;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
