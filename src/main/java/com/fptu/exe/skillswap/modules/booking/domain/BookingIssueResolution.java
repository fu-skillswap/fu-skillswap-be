package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only financial audit snapshot for a booking dispute decision. It is never used as a
 * mutable configuration record: a future correction must create a linked reversal record.
 */
@Entity
@Table(name = "booking_issue_resolutions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingIssueResolution {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "resolved_by_user_id", nullable = false)
    private UUID resolvedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_kind", nullable = false, length = 20)
    @Builder.Default
    private BookingIssueResolutionKind resolutionKind = BookingIssueResolutionKind.RESOLUTION;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AdminBookingIssueResolutionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 60)
    private AdminBookingIssueResolutionReasonCode reasonCode;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "mentee_bps")
    private Integer menteeBps;

    @Column(name = "mentor_bps")
    private Integer mentorBps;

    @Column(name = "platform_bps")
    private Integer platformBps;

    @Column(name = "escrow_scoin", nullable = false)
    @Builder.Default
    private Integer escrowScoin = 0;

    @Column(name = "mentee_refund_scoin", nullable = false)
    @Builder.Default
    private Integer menteeRefundScoin = 0;

    @Column(name = "mentor_settlement_scoin", nullable = false)
    @Builder.Default
    private Integer mentorSettlementScoin = 0;

    @Column(name = "platform_settlement_scoin", nullable = false)
    @Builder.Default
    private Integer platformSettlementScoin = 0;

    @Column(name = "settlement_applied_at_utc")
    private Instant settlementAppliedAtUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BookingIssueResolutionStatus status = BookingIssueResolutionStatus.APPLIED;

    @Column(name = "reversal_of_resolution_id")
    private UUID reversalOfResolutionId;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Version
    @Builder.Default
    private Integer version = 0;
}
