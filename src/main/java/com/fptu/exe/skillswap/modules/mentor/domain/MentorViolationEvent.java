package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_violation_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_mentor_violation_operation", columnNames = "operation_key"),
        indexes = {
                @Index(name = "idx_mentor_violation_mentor_time", columnList = "mentor_user_id,occurred_at"),
                @Index(name = "idx_mentor_violation_booking", columnList = "booking_id")
        })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorViolationEvent {

    @Id
    private UUID id;

    @Column(name = "mentor_user_id", nullable = false)
    private UUID mentorUserId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_module", nullable = false, length = 30)
    private MentorViolationSource sourceModule;

    @Column(name = "source_reference_id")
    private UUID sourceReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false, length = 60)
    private MentorViolationType violationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MentorViolationSeverity severity;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal points;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "decision_by_user_id")
    private UUID decisionByUserId;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "operation_key", nullable = false, length = 160)
    private String operationKey;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Setter
    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Setter
    @Column(name = "reversed_by_user_id")
    private UUID reversedByUserId;

    @Setter
    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = DateTimeUtil.now();
        if (createdAt == null) createdAt = DateTimeUtil.now();
    }
}
