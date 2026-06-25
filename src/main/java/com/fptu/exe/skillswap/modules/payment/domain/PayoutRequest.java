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

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        LocalDateTime now = DateTimeUtil.now();
        requestedAt = requestedAt == null ? now : requestedAt;
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
