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
@Table(name = "coupon_redemptions", indexes = {
        @Index(name = "idx_coupon_redemptions_coupon_id", columnList = "coupon_id"),
        @Index(name = "idx_coupon_redemptions_order_id", columnList = "payment_order_id", unique = true),
        @Index(name = "idx_coupon_redemptions_user_id", columnList = "redeemer_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRedemption {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "payment_order_id", nullable = false, unique = true)
    private UUID paymentOrderId;

    @Column(name = "redeemer_user_id", nullable = false)
    private UUID redeemerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CouponRedemptionStatus status = CouponRedemptionStatus.RESERVED;

    @Column(name = "discount_scoin", nullable = false)
    private Integer discountScoin;

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
