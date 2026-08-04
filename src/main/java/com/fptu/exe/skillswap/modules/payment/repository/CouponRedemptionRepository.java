package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    Optional<CouponRedemption> findByPaymentOrderId(UUID paymentOrderId);

    Page<CouponRedemption> findByCouponId(UUID couponId, Pageable pageable);

    long countByCouponId(UUID couponId);

    long countByCouponIdAndStatusIn(UUID couponId, java.util.Collection<CouponRedemptionStatus> statuses);

    long countByCouponIdAndRedeemerUserIdAndStatusIn(UUID couponId, UUID redeemerUserId, java.util.Collection<CouponRedemptionStatus> statuses);
}
