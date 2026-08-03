package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "availability_template_reconciliation", indexes = @Index(name = "idx_availability_template_reconciliation_due", columnList = "next_reconcile_at, claimed_until"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityTemplateReconciliation {
    @Id
    @Column(name = "template_id")
    private UUID templateId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "fk_availability_template_reconciliation_template"))
    private AvailabilityTemplate template;

    @Column(name = "last_reconciled_at")
    private LocalDateTime lastReconciledAt;
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
    @Column(name = "next_reconcile_at", nullable = false)
    private LocalDateTime nextReconcileAt;
    @Column(name = "claim_token")
    private UUID claimToken;
    @Column(name = "claimed_until")
    private LocalDateTime claimedUntil;
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;
    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;
    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private int consecutiveFailures = 0;

    @PrePersist
    void onCreate() {
        if (nextReconcileAt == null) nextReconcileAt = DateTimeUtil.now();
    }
}
