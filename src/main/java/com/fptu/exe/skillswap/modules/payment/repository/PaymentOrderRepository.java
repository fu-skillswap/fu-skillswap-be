package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByBookingId(UUID bookingId);

    Optional<PaymentOrder> findByOrderCode(String orderCode);

    Optional<PaymentOrder> findByProviderOrderCode(String providerOrderCode);

    boolean existsByBookingId(UUID bookingId);

    boolean existsByProviderOrderCode(String providerOrderCode);

    boolean existsByBookingIdAndStatus(UUID bookingId, PaymentOrderStatus status);
}
