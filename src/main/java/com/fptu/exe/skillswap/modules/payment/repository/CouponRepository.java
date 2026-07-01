package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coupon from Coupon coupon where coupon.code = :code")
    Optional<Coupon> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coupon from Coupon coupon where coupon.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByCode(String code);

    long countByIdAndStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqual(UUID id,
                                                                          CouponStatus status,
                                                                          LocalDateTime startAt,
                                                                          LocalDateTime endAt);
}
