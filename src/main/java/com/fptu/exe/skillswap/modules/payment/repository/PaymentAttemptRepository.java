package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByPaymentOrderIdOrderByAttemptNoAsc(UUID paymentOrderId);

    Optional<PaymentAttempt> findFirstByPaymentOrderIdOrderByAttemptNoDesc(UUID paymentOrderId);

    Optional<PaymentAttempt> findByProviderOrderCode(String providerOrderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pa from PaymentAttempt pa where pa.providerOrderCode = :providerOrderCode")
    Optional<PaymentAttempt> findByProviderOrderCodeForUpdate(@Param("providerOrderCode") String providerOrderCode);

    boolean existsByProviderTransactionId(String providerTransactionId);

    boolean existsByProviderEventId(String providerEventId);

    long countByPaymentOrderId(UUID paymentOrderId);

    long countByPaymentOrderIdAndStatus(UUID paymentOrderId, PaymentAttemptStatus status);
}
