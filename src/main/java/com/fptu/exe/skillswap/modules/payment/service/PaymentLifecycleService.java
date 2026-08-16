package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentLifecycleService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditLedgerService creditLedgerService;
    private final CouponService couponService;
    private final SettlementService settlementService;

    @Transactional
    public void handleMenteeCancellation(Booking booking, boolean lateCancellation) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
        if (order == null) {
            return;
        }
        if (isAwaitingPayment(order.getStatus())) {
            cancelAwaitingPaymentOrder(order);
            return;
        }
        if (order.getStatus() != PaymentOrderStatus.PAID) {
            return;
        }
        if (order.getCancelledAt() == null) {
            order.setCancelledAt(DateTimeUtil.now());
            paymentOrderRepository.save(order);
        }
        if (settlementService != null) {
            settlementService.handlePaidBookingCancelledByMentee(booking, order, lateCancellation);
        }
    }

    @Transactional
    public void handleMentorCancellation(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
        if (order == null) {
            return;
        }
        if (isAwaitingPayment(order.getStatus())) {
            cancelAwaitingPaymentOrder(order);
            return;
        }
        if (order.getStatus() != PaymentOrderStatus.PAID) {
            return;
        }
        if (order.getCancelledAt() == null) {
            order.setCancelledAt(DateTimeUtil.now());
            paymentOrderRepository.save(order);
        }
        if (settlementService != null) {
            settlementService.handlePaidBookingCancelledByMentor(booking, order);
        }
    }

    @Transactional
    public void expireAwaitingPayment(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
        if (order == null) {
            return;
        }
        if (isAwaitingPayment(order.getStatus())) {
            expireAwaitingPaymentOrder(order);
        }
    }

    public void cancelAwaitingPaymentOrder(PaymentOrder order) {
        if (order == null || isFinal(order.getStatus())) {
            return;
        }
        order.setStatus(PaymentOrderStatus.CANCELLED);
        order.setCancelledAt(DateTimeUtil.now());
        rollbackReservedCredit(order);
        if (couponService != null) {
            couponService.voidRedemption(order.getId());
        }
        paymentOrderRepository.save(order);
    }

    public void expireAwaitingPaymentOrder(PaymentOrder order) {
        if (order == null || isFinal(order.getStatus())) {
            return;
        }
        order.setStatus(PaymentOrderStatus.EXPIRED);
        order.setFailedAt(DateTimeUtil.now());
        rollbackReservedCredit(order);
        if (couponService != null) {
            couponService.voidRedemption(order.getId());
        }
        paymentOrderRepository.save(order);
    }

    public void rollbackReservedCredit(PaymentOrder order) {
        if (creditLedgerService != null && order != null && order.getPayerUserId() != null) {
            creditLedgerService.releaseReservedCredit(
                    order.getPayerUserId(),
                    LedgerSourceType.PAYMENT_ORDER,
                    order.getId(),
                    "Rollback reserved credit for payment order " + order.getOrderCode()
            );
        }
    }

    public static boolean isAwaitingPayment(PaymentOrderStatus status) {
        return status == PaymentOrderStatus.PENDING
                || status == PaymentOrderStatus.PARTIALLY_COVERED_BY_CREDIT
                || status == PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT;
    }

    public static boolean isFinal(PaymentOrderStatus status) {
        return status == PaymentOrderStatus.PAID
                || status == PaymentOrderStatus.FAILED
                || status == PaymentOrderStatus.CANCELLED
                || status == PaymentOrderStatus.EXPIRED;
    }
}
