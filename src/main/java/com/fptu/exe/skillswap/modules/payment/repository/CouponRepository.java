package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    long countByIdAndStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqual(UUID id,
                                                                          CouponStatus status,
                                                                          LocalDateTime startAt,
                                                                          LocalDateTime endAt);
}
