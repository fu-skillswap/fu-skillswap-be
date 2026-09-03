package com.fptu.exe.skillswap.modules.payment.domain;

import com.fptu.exe.skillswap.modules.payment.port.PaymentStatusContract;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_orders", indexes = {
        @Index(name = "idx_payment_orders_order_code", columnList = "order_code", unique = true),
        @Index(name = "idx_payment_orders_target", columnList = "target_type, target_id", unique = true),
        @Index(name = "idx_payment_orders_payer_id", columnList = "payer_user_id"),
        @Index(name = "idx_payment_orders_status", columnList = "status"),
        @Index(name = "idx_payment_orders_provider_order_code", columnList = "provider_order_code", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true, length = 80)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private PaymentTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "payer_user_id", nullable = false)
    private UUID payerUserId;

    @Column(name = "mentor_user_id", nullable = false)
    private UUID mentorUserId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "gross_scoin", nullable = false)
    @Builder.Default
    private Integer grossScoin = 0;

    @Column(name = "commission_rate_bps", nullable = false)
    @Builder.Default
    private Integer commissionRateBps = 0;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "coupon_code_snapshot", length = 100)
    private String couponCodeSnapshot;

    @Column(name = "coupon_discount_scoin", nullable = false)
    @Builder.Default
    private Integer couponDiscountScoin = 0;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "campaign_name_snapshot", length = 150)
    private String campaignNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_funding_source", length = 30)
    private FundingSource campaignFundingSource;

    @Column(name = "campaign_credit_scoin", nullable = false)
    @Builder.Default
    private Integer campaignCreditScoin = 0;

    @Column(name = "user_credit_scoin", nullable = false)
    @Builder.Default
    private Integer userCreditScoin = 0;

    @Column(name = "remaining_payable_scoin", nullable = false)
    @Builder.Default
    private Integer remainingPayableScoin = 0;

    @Column(name = "mentor_net_scoin", nullable = false)
    @Builder.Default
    private Integer mentorNetScoin = 0;

    @Column(name = "commission_scoin", nullable = false)
    @Builder.Default
    private Integer commissionScoin = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;

    /** Converts the payment state to the narrow value exposed to consuming modules. */
    public PaymentStatusContract toStatusContract() {
        return new PaymentStatusContract(
                status == null ? null : status.name(),
                settlementStatus == null ? null : settlementStatus.name()
        );
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 30)
    @Builder.Default
    private PaymentProvider paymentProvider = PaymentProvider.PAYOS;

    @Column(name = "provider_order_code", unique = true, length = 100)
    private String providerOrderCode;

    @Column(name = "provider_payment_link_id", length = 120)
    private String providerPaymentLinkId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "provider_transaction_id", unique = true, length = 100)
    private String providerTransactionId;

    @Column(name = "provider_event_id", unique = true, length = 100)
    private String providerEventId;

    @Column(name = "payment_link", columnDefinition = "TEXT")
    private String paymentLink;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "expires_at_utc")
    private Instant expiresAtUtc;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "paid_at_utc")
    private Instant paidAtUtc;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_at_utc")
    private Instant cancelledAtUtc;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failed_at_utc")
    private Instant failedAtUtc;

    @Column(name = "credit_finalized_at")
    private LocalDateTime creditFinalizedAt;

    @Column(name = "credit_finalized_at_utc")
    private Instant creditFinalizedAtUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", length = 20)
    private PaymentSettlementStatus settlementStatus;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "released_at_utc")
    private Instant releasedAtUtc;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refunded_at_utc")
    private Instant refundedAtUtc;

    @Column(name = "refunded_scoin")
    private Integer refundedScoin;

    @Column(name = "refund_reason", length = 120)
    private String refundReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        Instant nowUtc = DateTimeUtil.instantNow();
        LocalDateTime nowHcm = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(nowUtc);
        if (createdAt == null && createdAtUtc == null) {
            createdAtUtc = nowUtc;
            createdAt = nowHcm;
        } else if (createdAtUtc != null && createdAt == null) {
            createdAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(createdAtUtc);
        } else if (createdAt != null && createdAtUtc == null) {
            createdAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(createdAt);
        }
        updatedAtUtc = nowUtc;
        updatedAt = nowHcm;
        syncShadowFields();
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAtUtc = DateTimeUtil.instantNow();
        updatedAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(updatedAtUtc);
        syncShadowFields();
    }

    private void syncShadowFields() {
        if (expiresAtUtc != null) {
            expiresAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(expiresAtUtc);
        } else if (expiresAt != null) {
            expiresAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(expiresAt);
        }

        if (paidAtUtc != null) {
            paidAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(paidAtUtc);
        } else if (paidAt != null) {
            paidAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(paidAt);
        }

        if (cancelledAtUtc != null) {
            cancelledAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(cancelledAtUtc);
        } else if (cancelledAt != null) {
            cancelledAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(cancelledAt);
        }

        if (failedAtUtc != null) {
            failedAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(failedAtUtc);
        } else if (failedAt != null) {
            failedAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(failedAt);
        }

        if (creditFinalizedAtUtc != null) {
            creditFinalizedAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(creditFinalizedAtUtc);
        } else if (creditFinalizedAt != null) {
            creditFinalizedAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(creditFinalizedAt);
        }

        if (releasedAtUtc != null) {
            releasedAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(releasedAtUtc);
        } else if (releasedAt != null) {
            releasedAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(releasedAt);
        }

        if (refundedAtUtc != null) {
            refundedAt = com.fptu.exe.skillswap.shared.time.BusinessTime.fromInstant(refundedAtUtc);
        } else if (refundedAt != null) {
            refundedAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(refundedAt);
        }
    }
}
