package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByPaymentOrderIdOrderByAttemptNoAsc(UUID paymentOrderId);

    Optional<PaymentAttempt> findFirstByPaymentOrderIdOrderByAttemptNoDesc(UUID paymentOrderId);

    boolean existsByProviderTransactionId(String providerTransactionId);

    long countByPaymentOrderId(UUID paymentOrderId);

    long countByPaymentOrderIdAndStatus(UUID paymentOrderId, PaymentAttemptStatus status);
}
