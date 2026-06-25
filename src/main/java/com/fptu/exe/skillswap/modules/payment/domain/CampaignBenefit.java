package com.fptu.exe.skillswap.modules.payment.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_benefits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignBenefit {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false, length = 30)
    private CampaignBenefitType benefitType;

    @Column(name = "credit_scoin")
    private Integer creditScoin;

    @Column(name = "coupon_code", length = 80)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_discount_type", length = 20)
    private CouponDiscountType couponDiscountType;

    @Column(name = "coupon_discount_value")
    private Integer couponDiscountValue;

    @Column(name = "coupon_max_discount_scoin")
    private Integer couponMaxDiscountScoin;

    @Column(name = "coupon_quota_total")
    private Integer couponQuotaTotal;

    @Column(name = "coupon_quota_per_user")
    private Integer couponQuotaPerUser;

    @Column(name = "coupon_min_order_value_scoin")
    private Integer couponMinOrderValueScoin;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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
