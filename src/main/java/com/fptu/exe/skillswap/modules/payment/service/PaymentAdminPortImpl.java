package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import com.fptu.exe.skillswap.modules.payment.port.PaymentAdminPort;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PayoutRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentAdminPortImpl implements PaymentAdminPort {

    private final PayoutRequestRepository payoutRequestRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    @Override
    public long countPendingPayoutRequests() {
        return payoutRequestRepository.countByStatus(PayoutRequestStatus.REQUESTED);
    }

    @Override
    public long countFailedPaymentOrders() {
        return paymentOrderRepository.countByStatus(PaymentOrderStatus.FAILED);
    }

    @Override
    public long countCompletedPaymentOrdersBetween(LocalDateTime start, LocalDateTime end) {
        return paymentOrderRepository.countByStatusAndCreatedAtBetween(PaymentOrderStatus.PAID, start, end);
    }

    @Override
    public long countTotalPaymentOrdersByUserId(UUID userId) {
        return userId == null ? 0 : paymentOrderRepository.countByPayerUserId(userId);
    }

    @Override
    public long countTotalPayoutRequestsByUserId(UUID userId) {
        return userId == null ? 0 : payoutRequestRepository.countByMentorUserId(userId);
    }

    @Override
    public boolean existsPaymentOrderById(UUID orderId) {
        return orderId != null && paymentOrderRepository.existsById(orderId);
    }

    @Override
    public boolean existsPayoutRequestById(UUID payoutRequestId) {
        return payoutRequestId != null && payoutRequestRepository.existsById(payoutRequestId);
    }

    @Override
    public List<String> paymentOrderStatusNames() {
        return java.util.Arrays.stream(PaymentOrderStatus.values()).map(Enum::name).toList();
    }

    @Override
    public List<String> payoutRequestStatusNames() {
        return java.util.Arrays.stream(PayoutRequestStatus.values()).map(Enum::name).toList();
    }
}
