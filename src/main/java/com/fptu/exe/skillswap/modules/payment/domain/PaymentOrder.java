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
@Table(name = "payment_orders", indexes = {
        @Index(name = "idx_payment_orders_order_code", columnList = "order_code", unique = true),
        @Index(name = "idx_payment_orders_booking_id", columnList = "booking_id", unique = true),
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

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

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

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "credit_finalized_at")
    private LocalDateTime creditFinalizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
