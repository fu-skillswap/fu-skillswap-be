package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentLifecycleService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditLedgerService creditLedgerService;
    private final CouponService couponService;
    private final SettlementService settlementService;
    private final BookingPaymentSettlementPort bookingPaymentSettlementPort;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public void handleMenteeCancellation(UUID bookingId, boolean lateCancellation) {
        if (bookingId == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId).orElse(null);
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
        if (order.getCancelledAtUtc() == null && order.getCancelledAt() == null) {
            Instant nowUtc = timeProvider.instant();
            order.setCancelledAtUtc(nowUtc);
            order.setCancelledAt(timeProvider.nowBusiness());
            paymentOrderRepository.save(order);
        }
        if (settlementService != null) {
            bookingPaymentSettlementPort.findCancellationContext(bookingId)
                    .ifPresent(context -> settlementService.handlePaidBookingCancelledByMentee(context, order, lateCancellation));
        }
    }

    @Transactional
    public void handleMentorCancellation(UUID bookingId) {
        if (bookingId == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId).orElse(null);
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
        if (order.getCancelledAtUtc() == null && order.getCancelledAt() == null) {
            Instant nowUtc = timeProvider.instant();
            order.setCancelledAtUtc(nowUtc);
            order.setCancelledAt(timeProvider.nowBusiness());
            paymentOrderRepository.save(order);
        }
        if (settlementService != null) {
            bookingPaymentSettlementPort.findCancellationContext(bookingId)
                    .ifPresent(context -> settlementService.handlePaidBookingCancelledByMentor(context, order));
        }
    }

    @Transactional
    public void expireAwaitingPayment(UUID bookingId) {
        if (bookingId == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, bookingId).orElse(null);
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
        order.setCancelledAtUtc(timeProvider.instant());
        order.setCancelledAt(timeProvider.nowBusiness());
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
        order.setFailedAtUtc(timeProvider.instant());
        order.setFailedAt(timeProvider.nowBusiness());
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
