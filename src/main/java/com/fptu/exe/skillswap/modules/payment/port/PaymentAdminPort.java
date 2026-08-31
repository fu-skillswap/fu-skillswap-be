package com.fptu.exe.skillswap.modules.payment.port;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

public interface PaymentAdminPort {
    long countPendingPayoutRequests();
    long countFailedPaymentOrders();
    long countCompletedPaymentOrdersBetween(LocalDateTime start, LocalDateTime end);
    long countTotalPaymentOrdersByUserId(UUID userId);
    long countTotalPayoutRequestsByUserId(UUID userId);
    boolean existsPaymentOrderById(UUID orderId);
    boolean existsPayoutRequestById(UUID payoutRequestId);
    List<String> paymentOrderStatusNames();
    List<String> payoutRequestStatusNames();
}
