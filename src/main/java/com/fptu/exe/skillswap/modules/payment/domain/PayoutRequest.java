package com.fptu.exe.skillswap.modules.payment.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payout_requests", indexes = {
        @Index(name = "idx_payout_requests_mentor_id", columnList = "mentor_user_id"),
        @Index(name = "idx_payout_requests_status", columnList = "status"),
        @Index(name = "idx_payout_requests_settlement_account", columnList = "settlement_account_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "mentor_user_id", nullable = false)
    private UUID mentorUserId;

    @Column(name = "settlement_account_id", nullable = false)
    private UUID settlementAccountId;

    @Column(name = "payout_profile_id", nullable = false)
    private UUID payoutProfileId;

    @Column(name = "amount_scoin", nullable = false)
    private Integer amountScoin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PayoutRequestStatus status = PayoutRequestStatus.REQUESTED;

    @Column(name = "admin_user_id")
    private UUID adminUserId;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "bank_account_name_snapshot", nullable = false, length = 150)
    private String bankAccountNameSnapshot;

    @Column(name = "bank_name_snapshot", nullable = false, length = 150)
    private String bankNameSnapshot;

    @Column(name = "bank_account_number_masked_snapshot", nullable = false, length = 30)
    private String bankAccountNumberMaskedSnapshot;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "requested_at_utc", nullable = false)
    private java.time.Instant requestedAtUtc;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_at_utc")
    private java.time.Instant reviewedAtUtc;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_at_utc")
    private java.time.Instant approvedAtUtc;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "paid_at_utc")
    private java.time.Instant paidAtUtc;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejected_at_utc")
    private java.time.Instant rejectedAtUtc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private java.time.Instant createdAtUtc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_at_utc", nullable = false)
    private java.time.Instant updatedAtUtc;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        java.time.Instant nowUtc = com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow();
        LocalDateTime nowHcm = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(nowUtc);
        if (requestedAtUtc == null && requestedAt == null) {
            requestedAtUtc = nowUtc;
            requestedAt = nowHcm;
        } else if (requestedAtUtc != null && requestedAt == null) {
            requestedAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(requestedAtUtc);
        } else if (requestedAt != null && requestedAtUtc == null) {
            requestedAtUtc = com.fptu.exe.skillswap.modules.booking.service.BookingTime.toInstant(requestedAt);
        }

        if (createdAt == null && createdAtUtc == null) {
            createdAtUtc = nowUtc;
            createdAt = nowHcm;
        } else if (createdAtUtc != null && createdAt == null) {
            createdAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(createdAtUtc);
        } else if (createdAt != null && createdAtUtc == null) {
            createdAtUtc = com.fptu.exe.skillswap.modules.booking.service.BookingTime.toInstant(createdAt);
        }
        updatedAtUtc = nowUtc;
        updatedAt = nowHcm;
        syncShadowFields();
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAtUtc = com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow();
        updatedAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(updatedAtUtc);
        syncShadowFields();
    }

    private void syncShadowFields() {
        if (reviewedAtUtc != null) {
            reviewedAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(reviewedAtUtc);
        } else if (reviewedAt != null) {
            reviewedAtUtc = com.fptu.exe.skillswap.modules.booking.service.BookingTime.toInstant(reviewedAt);
        }

        if (approvedAtUtc != null) {
            approvedAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(approvedAtUtc);
        } else if (approvedAt != null) {
            approvedAtUtc = com.fptu.exe.skillswap.modules.booking.service.BookingTime.toInstant(approvedAt);
        }

        if (paidAtUtc != null) {
            paidAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(paidAtUtc);
        } else if (paidAt != null) {
            paidAtUtc = com.fptu.exe.skillswap.modules.booking.service.BookingTime.toInstant(paidAt);
        }

        if (rejectedAtUtc != null) {
            rejectedAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(rejectedAtUtc);
        } else if (rejectedAt != null) {
            rejectedAtUtc = com.fptu.exe.skillswap.modules.booking.service.BookingTime.toInstant(rejectedAt);
        }
    }
}
