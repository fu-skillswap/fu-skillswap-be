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
@Table(name = "payment_attempts", indexes = {
        @Index(name = "idx_payment_attempts_order_id", columnList = "payment_order_id"),
        @Index(name = "idx_payment_attempts_status", columnList = "status"),
        @Index(name = "idx_payment_attempts_provider_txn", columnList = "provider_transaction_id", unique = true),
        @Index(name = "idx_payment_attempts_provider_order_code", columnList = "provider_order_code", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAttempt {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "payment_order_id", nullable = false)
    private UUID paymentOrderId;

    @Column(name = "attempt_no", nullable = false)
    @Builder.Default
    private Integer attemptNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentAttemptStatus status = PaymentAttemptStatus.PENDING;

    @Column(name = "provider_order_code", length = 100)
    private String providerOrderCode;

    @Column(name = "provider_payment_link_id", length = 120)
    private String providerPaymentLinkId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "provider_transaction_id", unique = true, length = 100)
    private String providerTransactionId;

    @Column(name = "provider_event_id", unique = true, length = 100)
    private String providerEventId;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

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
