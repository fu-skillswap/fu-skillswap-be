package com.fptu.exe.skillswap.modules.payment.port;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PaymentAdminPort {
    long countPendingPayoutRequests();
    long countFailedPaymentOrders();
    long countCompletedPaymentOrdersBetween(LocalDateTime start, LocalDateTime end);
    long countTotalPaymentOrdersByUserId(UUID userId);
    long countTotalPayoutRequestsByUserId(UUID userId);
}
