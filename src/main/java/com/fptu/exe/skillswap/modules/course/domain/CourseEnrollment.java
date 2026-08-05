package com.fptu.exe.skillswap.modules.course.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
    name = "course_enrollments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_enrollments_student", columnNames = {"course_id", "student_user_id"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollment {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_course_enrollments_course"))
    private Course course;

    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Column(name = "payment_order_id")
    private UUID paymentOrderId;

    @Column(name = "base_price_scoin", nullable = false)
    @Builder.Default
    private int basePriceScoin = 0;

    @Column(name = "buyer_fee_scoin", nullable = false)
    @Builder.Default
    private int buyerFeeScoin = 0;

    @Column(name = "mentor_commission_scoin", nullable = false)
    @Builder.Default
    private int mentorCommissionScoin = 0;

    @Column(name = "mentor_payout_scoin", nullable = false)
    @Builder.Default
    private int mentorPayoutScoin = 0;

    @Column(name = "paid_amount_scoin", nullable = false)
    private int paidAmountScoin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.PENDING_PAYMENT;

    @Column(name = "seat_reserved_until")
    private Instant seatReservedUntil;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        enrolledAt = Instant.now();
        updatedAt = enrolledAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
