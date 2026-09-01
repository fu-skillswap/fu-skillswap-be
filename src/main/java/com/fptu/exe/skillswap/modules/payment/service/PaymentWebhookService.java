package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingCancellationContext;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentSettlementStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProvider;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSnapshot;
import com.fptu.exe.skillswap.shared.time.BusinessTime;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.payment.service.PaymentLifecycleService.isFinal;

@Slf4j
@Service
public class PaymentWebhookService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CreditLedgerService creditLedgerService;
    private final CouponService couponService;
    private final SettlementService settlementService;
    private final BookingPaymentSettlementPort bookingPaymentSettlementPort;
    private final PaymentGatewayProviderFactory paymentGatewayProviderFactory;
    private final PaymentLifecycleService paymentLifecycleService;
    private final PaymentResponseMapper paymentResponseMapper;
    private final InternalTelemetryService internalTelemetryService;
    private final TransactionTemplate transactionTemplate;
    private final PaymentProperties paymentProperties;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired
    public PaymentWebhookService(
            PaymentOrderRepository paymentOrderRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            CreditLedgerService creditLedgerService,
            CouponService couponService,
            SettlementService settlementService,
            BookingPaymentSettlementPort bookingPaymentSettlementPort,
            PaymentGatewayProviderFactory paymentGatewayProviderFactory,
            PaymentLifecycleService paymentLifecycleService,
            PaymentResponseMapper paymentResponseMapper,
            InternalTelemetryService internalTelemetryService,
            TransactionTemplate transactionTemplate,
            PaymentProperties paymentProperties) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.creditLedgerService = creditLedgerService;
        this.couponService = couponService;
        this.settlementService = settlementService;
        this.bookingPaymentSettlementPort = bookingPaymentSettlementPort;
        this.paymentGatewayProviderFactory = paymentGatewayProviderFactory;
        this.paymentLifecycleService = paymentLifecycleService;
        this.paymentResponseMapper = paymentResponseMapper;
        this.internalTelemetryService = internalTelemetryService;
        this.transactionTemplate = transactionTemplate;
        this.paymentProperties = paymentProperties;
    }

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    public PaymentCheckoutResponse handleWebhook(PaymentWebhookRequest request) {
        if (request == null || request.data() == null || request.data().orderCode() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Webhook PayOS thiếu data.orderCode");
        }

        PaymentGatewayProvider.VerifiedWebhook verified = paymentGatewayProviderFactory.getProvider(PaymentProvider.PAYOS).verifyWebhook(request);
        boolean paidWebhook = verified.success() && isPaidProviderWebhook(verified.providerStatus());
        boolean terminalWebhook = isTerminalProviderWebhook(verified.providerStatus());
        if (!paidWebhook && !terminalWebhook) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Webhook PayOS chưa xác nhận thanh toán thành công");
        }

        PaymentAttempt optimisticAttempt = paymentAttemptRepository.findByProviderOrderCode(verified.providerOrderCode())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment attempt"));
        if (optimisticAttempt.getStatus() == PaymentAttemptStatus.SUCCEEDED
                || optimisticAttempt.getStatus() == PaymentAttemptStatus.SUCCEEDED_SURPLUS) {
            PaymentOrder optimisticOrder = paymentOrderRepository.findById(optimisticAttempt.getPaymentOrderId()).orElseThrow();
            return paymentResponseMapper.toResponse(optimisticOrder, optimisticAttempt);
        }

        return transactionTemplate.execute(status -> {
            // Giữ thứ tự lock thống nhất với checkout/expiry: booking -> payment order -> payment attempt.
            // Không lock attempt theo providerOrderCode trước, nếu không webhook và luồng hủy có thể chờ chéo nhau.
            UUID targetId = paymentOrderRepository.findTargetIdById(optimisticAttempt.getPaymentOrderId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
            BookingPaymentSnapshot lockedBooking = bookingPaymentSettlementPort.findPaymentSnapshotForUpdate(targetId)
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để hoàn tất thanh toán"));
            PaymentOrder order = paymentOrderRepository.findByIdForUpdate(optimisticAttempt.getPaymentOrderId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
            PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(optimisticAttempt.getId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                            "Không tìm thấy payment attempt tương ứng với orderCode PayOS"));

            String providerEventId = resolveProviderEventId(verified);
            if (StringUtils.hasText(providerEventId)
                    && (paymentOrderRepository.existsByProviderEventId(providerEventId)
                    || paymentAttemptRepository.existsByProviderEventId(providerEventId))) {
                return paymentResponseMapper.toResponse(order, attempt);
            }
            if (terminalWebhook) {
                if (isFinal(order.getStatus())) {
                    return paymentResponseMapper.toResponse(order, attempt);
                }
                applyTerminalWebhook(order, attempt, verified, providerEventId);
                order = paymentOrderRepository.save(order);
                return paymentResponseMapper.toResponse(order, attempt);
            }
            if (order.getStatus() == PaymentOrderStatus.PAID) {
                if (attempt.getStatus() != PaymentAttemptStatus.SUCCEEDED && attempt.getStatus() != PaymentAttemptStatus.SUCCEEDED_SURPLUS) {
                    attempt.setProviderOrderCode(verified.providerOrderCode());
                    attempt.setProviderPaymentLinkId(verified.providerPaymentLinkId());
                    attempt.setProviderStatus("PAID");
                    issueSurplusCreditIfNeeded(order, attempt, verified.amount(), expectedProviderPayable(order));
                    markAttemptFinalState(attempt, PaymentAttemptStatus.SUCCEEDED_SURPLUS, verified.providerTransactionId(), providerEventId, "PAID", null);
                }
                return paymentResponseMapper.toResponse(order, attempt);
            }

            validateProviderPaidAmount(order, verified.amount());
            order.setProviderOrderCode(verified.providerOrderCode());
            order.setProviderPaymentLinkId(verified.providerPaymentLinkId());
            order.setProviderStatus("PAID");
            order.setProviderTransactionId(verified.providerTransactionId());
            order.setProviderEventId(providerEventId);
            if (verified.paidAtUtc() != null) {
                order.setPaidAtUtc(verified.paidAtUtc());
                order.setPaidAt(verified.paidAt());
            } else if (verified.paidAt() != null) {
                order.setPaidAt(verified.paidAt());
                order.setPaidAtUtc(BusinessTime.toInstant(verified.paidAt()));
            } else {
                order.setPaidAtUtc(timeProvider.instant());
                order.setPaidAt(timeProvider.nowBusiness());
            }

            attempt.setProviderOrderCode(verified.providerOrderCode());
            attempt.setProviderPaymentLinkId(verified.providerPaymentLinkId());
            attempt.setProviderStatus("PAID");
            finalizeInternalPayment(order, attempt, verified.providerTransactionId(), providerEventId, "PAID", lockedBooking);
            issueSurplusCreditIfNeeded(order, attempt, verified.amount(), expectedProviderPayable(order));
            order = paymentOrderRepository.save(order);
            return paymentResponseMapper.toResponse(order, attempt);
        });
    }

    public void synchronizeProviderStatusForBooking(UUID bookingId) {
        if (bookingId == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId).orElse(null);
        if (order == null) {
            return;
        }
        PaymentAttempt latestAttempt = paymentAttemptRepository
                .findFirstByPaymentOrderIdOrderByAttemptNoDesc(order.getId())
                .orElse(null);
        if (latestAttempt != null) {
            trySynchronizeProviderStatus(order, latestAttempt);
        }
    }

    public void reconcileStaleProviderPayments() {
        long startedAtNanos = System.nanoTime();
        long deadlineNanos = startedAtNanos + Duration.ofSeconds(paymentProperties.getReconciliationMaxDurationSeconds()).toNanos();
        Instant thresholdUtc = timeProvider.instant().minus(java.time.Duration.ofMinutes(15));
        LocalDateTime thresholdLegacy = timeProvider.nowBusiness().minusMinutes(15);
        List<PaymentOrder> staleOrders = paymentOrderRepository.findTop50ByStatusInAndUpdatedAtUtcBeforeOrderByUpdatedAtUtcAsc(
                List.of(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT, PaymentOrderStatus.PARTIALLY_COVERED_BY_CREDIT),
                thresholdUtc,
                thresholdLegacy,
                PageRequest.of(0, paymentProperties.getReconciliationMaxOrdersPerRun())
        );
        int processed = 0;
        for (PaymentOrder order : staleOrders) {
            // This does not interrupt an in-flight provider call. It prevents one slow batch
            // from consuming the scheduler for an unbounded number of sequential calls.
            if (System.nanoTime() >= deadlineNanos) {
                log.warn("metric_name=payment_reconciliation_time_budget_reached_total processed={} candidates={} budget_seconds={}",
                        processed, staleOrders.size(), paymentProperties.getReconciliationMaxDurationSeconds());
                break;
            }
            try {
                synchronizeProviderStatusForBooking(order.getTargetId());
            } catch (RuntimeException ex) {
                log.warn("Failed to reconcile payment order {} for booking {}: {}", order.getId(), order.getTargetId(), ex.getMessage());
            }
            processed++;
        }
        if (processed > 0) {
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
            log.info("Payment reconciliation batch completed: processed={}, candidates={}, elapsed_ms={}",
                    processed, staleOrders.size(), elapsedMillis);
        }
    }

    public void trySynchronizeProviderStatus(PaymentOrder order, PaymentAttempt attempt) {
        if (order == null || attempt == null || isFinal(order.getStatus()) || !StringUtils.hasText(attempt.getProviderOrderCode())) {
            return;
        }
        if (order.getRemainingPayableScoin() == null || order.getRemainingPayableScoin() <= 0) {
            return;
        }
        try {
            PaymentGatewayProvider.PaymentLinkDetails paymentLink = paymentGatewayProviderFactory.getProvider(PaymentProvider.PAYOS)
                    .getPaymentLink(parseProviderOrderCode(attempt.getProviderOrderCode()));
            String providerStatus = paymentLink.providerStatus() == null
                    ? ""
                    : paymentLink.providerStatus().toUpperCase(Locale.ROOT);
            transactionTemplate.executeWithoutResult(status -> synchronizeProviderStatusInTransaction(
                    order.getTargetId(), order.getId(), attempt.getId(), paymentLink, providerStatus));
        } catch (Exception ex) {
            log.warn("Không thể đồng bộ trạng thái PayOS cho paymentOrderId={}: {}", order.getId(), ex.getMessage());
        }
    }

    private void synchronizeProviderStatusInTransaction(UUID bookingId,
                                                        UUID paymentOrderId,
                                                        UUID paymentAttemptId,
                                                        PaymentGatewayProvider.PaymentLinkDetails paymentLink,
                                                        String providerStatus) {
        // Canonical lock order for every booking payment mutation:
        // booking -> payment order -> payment attempt.
        BookingPaymentSnapshot booking = bookingPaymentSettlementPort.findPaymentSnapshotForUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để đồng bộ thanh toán"));
        PaymentOrder order = paymentOrderRepository.findByIdForUpdate(paymentOrderId).orElseThrow();
        PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(paymentAttemptId).orElseThrow();
        if (!paymentOrderId.equals(attempt.getPaymentOrderId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Payment attempt không thuộc payment order cần đồng bộ");
        }
        if (isFinal(order.getStatus())) {
            return;
        }
        order.setProviderStatus(paymentLink.providerStatus());
        order.setProviderPaymentLinkId(paymentLink.providerPaymentLinkId());
        attempt.setProviderStatus(paymentLink.providerStatus());
        attempt.setProviderPaymentLinkId(paymentLink.providerPaymentLinkId());
        switch (providerStatus) {
            case "PAID", "SUCCESS", "00" -> {
                finalizeInternalPayment(order, attempt, attempt.getProviderTransactionId(),
                        attempt.getProviderEventId(), paymentLink.providerStatus(), booking);
            }
            case "CANCELLED" -> {
                order.setStatus(PaymentOrderStatus.CANCELLED);
                Instant cancelledAtUtc = paymentLink.cancelledAtUtc() != null
                        ? paymentLink.cancelledAtUtc()
                        : timeProvider.instant();
                order.setCancelledAtUtc(cancelledAtUtc);
                order.setCancelledAt(BusinessTime.fromInstant(cancelledAtUtc));
                paymentLifecycleService.rollbackReservedCredit(order);
                if (couponService != null) {
                    couponService.voidRedemption(order.getId());
                }
                markAttemptFinalState(attempt, PaymentAttemptStatus.CANCELLED, attempt.getProviderTransactionId(),
                        attempt.getProviderEventId(), providerStatus, "PayOS payment link bị hủy");
            }
            case "EXPIRED" -> {
                order.setStatus(PaymentOrderStatus.EXPIRED);
                order.setFailedAtUtc(timeProvider.instant());
                order.setFailedAt(timeProvider.nowBusiness());
                paymentLifecycleService.rollbackReservedCredit(order);
                if (couponService != null) {
                    couponService.voidRedemption(order.getId());
                }
                markAttemptFinalState(attempt, PaymentAttemptStatus.EXPIRED, attempt.getProviderTransactionId(),
                        attempt.getProviderEventId(), providerStatus, "PayOS payment link đã hết hạn");
            }
            case "FAILED" -> {
                order.setStatus(PaymentOrderStatus.FAILED);
                order.setFailedAtUtc(timeProvider.instant());
                order.setFailedAt(timeProvider.nowBusiness());
                paymentLifecycleService.rollbackReservedCredit(order);
                if (couponService != null) {
                    couponService.voidRedemption(order.getId());
                }
                markAttemptFinalState(attempt, PaymentAttemptStatus.FAILED, attempt.getProviderTransactionId(),
                        attempt.getProviderEventId(), providerStatus, "PayOS payment link thất bại");
            }
            default -> paymentAttemptRepository.save(attempt);
        }
        paymentOrderRepository.save(order);
    }

    public void finalizeInternalPayment(PaymentOrder order,
                                        PaymentAttempt attempt,
                                        String providerTransactionId,
                                        String providerEventId,
                                        String providerStatus,
                                        BookingPaymentSnapshot lockedBooking) {
        // UTC is the authoritative value. Check both columns while the legacy
        // business-zone shadow column still exists so a partially migrated row
        // can never consume the same reserved credit twice.
        if (order.getCreditFinalizedAtUtc() == null && order.getCreditFinalizedAt() == null) {
            if (order.getRemainingPayableScoin() != null && order.getRemainingPayableScoin() > 0) {
                creditLedgerService.issueCredit(
                        order.getPayerUserId(),
                        CreditOriginType.MANUAL,
                        LedgerSourceType.PAYMENT_ORDER,
                        order.getId(),
                        order.getRemainingPayableScoin(),
                        "PayOS deposit for payment order " + order.getOrderCode()
                );
                creditLedgerService.reserveCredit(
                        order.getPayerUserId(),
                        order.getRemainingPayableScoin(),
                        LedgerSourceType.PAYMENT_ORDER,
                        order.getId(),
                        List.of(CreditOriginType.MANUAL),
                        "Reserve PayOS deposit for payment order " + order.getOrderCode()
                );
            }
            creditLedgerService.consumeReservedCredit(
                    order.getPayerUserId(),
                    LedgerSourceType.PAYMENT_ORDER,
                    order.getId(),
                    "Consume reserved credit for payment order " + order.getOrderCode()
            );
            order.setCreditFinalizedAtUtc(timeProvider.instant());
            order.setCreditFinalizedAt(timeProvider.nowBusiness());
        }
        if (couponService != null) {
            couponService.markRedeemed(order.getId());
        }
        order.setStatus(PaymentOrderStatus.PAID);
        order.setSettlementStatus(PaymentSettlementStatus.HELD);
        order.setProviderTransactionId(providerTransactionId);
        order.setProviderEventId(providerEventId);
        order.setProviderStatus(providerStatus);
        if (order.getPaidAtUtc() == null) {
            order.setPaidAtUtc(timeProvider.instant());
            order.setPaidAt(timeProvider.nowBusiness());
        }
        markAttemptFinalState(attempt, PaymentAttemptStatus.SUCCEEDED, providerTransactionId, providerEventId, providerStatus, null);
        finalizePaidBooking(order, lockedBooking);
    }

    public void finalizeInternalPayment(PaymentOrder order,
                                        PaymentAttempt attempt,
                                        String providerTransactionId,
                                        String providerEventId,
                                        String providerStatus) {
        BookingPaymentSnapshot booking = bookingPaymentSettlementPort
                .findPaymentSnapshotForUpdate(order.getTargetId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để hoàn tất thanh toán"));
        finalizeInternalPayment(order, attempt, providerTransactionId, providerEventId, providerStatus, booking);
    }

    void finalizePaidBooking(PaymentOrder order, BookingPaymentSnapshot lockedBooking) {
        if (lockedBooking == null) {
            throw new IllegalArgumentException(
                    "Booking phải được khóa trước PaymentOrder và PaymentAttempt khi hoàn tất thanh toán");
        }
        BookingPaymentSnapshot booking = lockedBooking;
        if ("PAID".equals(booking.paymentStatus())) {
            bookingPaymentSettlementPort.ensurePaidSideEffects(booking.bookingId());
            return;
        }
        if (!"ACCEPTED_AWAITING_PAYMENT".equals(booking.paymentStatus())) {
            compensateCapturedPaymentForTerminalBooking(booking, order);
            log.warn("finalizePaidBooking: booking {} ở trạng thái {} không thể chuyển sang PAID. " +
                            "Payment order vẫn được ghi nhận PAID và hệ thống đã chạy bù trừ nội bộ nếu cần.",
                    booking.bookingId(), booking.paymentStatus());
            return;
        }
        Instant paidAtUtc = order.getPaidAtUtc() != null ? order.getPaidAtUtc() : timeProvider.instant();
        if (booking.paymentExpiresAtUtc() != null && !booking.paymentExpiresAtUtc().isAfter(paidAtUtc)) {
            bookingPaymentSettlementPort.expirePayment(booking.bookingId(), paidAtUtc,
                    "Yêu cầu đặt lịch đã hết hạn trước khi cổng thanh toán xác nhận giao dịch.");
            compensateCapturedPaymentForTerminalBooking(booking, order);
            log.warn("finalizePaidBooking: payment order {} was paid after booking {} payment deadline; refunding capture",
                    order.getId(), booking.bookingId());
            return;
        }
        bookingPaymentSettlementPort.confirmPayment(booking.bookingId(), timeProvider.instant());
        if (internalTelemetryService != null) {
            internalTelemetryService.record(
                    "BOOKING_PAYMENT_CONFIRMED",
                    booking.payerUserId(),
                    "BOOKING",
                    booking.bookingId(),
                    Map.of(
                            "mentorUserId", String.valueOf(booking.mentorUserId()),
                            "grossScoin", String.valueOf(order.getGrossScoin()),
                            "remainingPayableScoin", String.valueOf(order.getRemainingPayableScoin())
                    )
            );
        }

    }

    private void compensateCapturedPaymentForTerminalBooking(BookingPaymentSnapshot booking, PaymentOrder order) {
        if (booking == null || order == null || settlementService == null) {
            return;
        }
        switch (booking.paymentStatus()) {
            case "CANCELLED_BY_MENTEE" -> {
                BookingCancellationContext context = bookingPaymentSettlementPort.findCancellationContext(booking.bookingId())
                        .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                                "Không tìm thấy trạng thái hủy booking để bù trừ thanh toán"));
                settlementService.handlePaidBookingCancelledByMentee(context, order, false);
            }
            case "CANCELLED_BY_MENTOR", "REJECTED", "EXPIRED" -> {
                BookingCancellationContext context = bookingPaymentSettlementPort.findCancellationContext(booking.bookingId())
                        .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                                "Không tìm thấy trạng thái hủy booking để bù trừ thanh toán"));
                settlementService.handlePaidBookingCancelledByMentor(context, order);
            }
            default -> {
            }
        }
    }

    private void validateProviderPaidAmount(PaymentOrder order, long verifiedAmount) {
        long expectedAmount = expectedProviderPayable(order);
        if (expectedAmount <= 0) {
            return;
        }
        if (verifiedAmount < expectedAmount) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Webhook PayOS xác nhận số tiền nhỏ hơn số tiền cần thanh toán"
            );
        }
    }

    private long expectedProviderPayable(PaymentOrder order) {
        return order == null || order.getRemainingPayableScoin() == null
                ? 0L
                : PricingPolicy.toVnd(order.getRemainingPayableScoin(), paymentProperties);
    }

    private void issueSurplusCreditIfNeeded(PaymentOrder order,
                                            PaymentAttempt attempt,
                                            long verifiedAmount,
                                            long expectedAmount) {
        if (order == null || attempt == null || attempt.getId() == null) {
            return;
        }
        long surplusAmount = verifiedAmount - Math.max(0L, expectedAmount);
        if (surplusAmount <= 0) {
            return;
        }
        if (creditLedgerService.hasIssuedCreditForSource(LedgerSourceType.PAYMENT_ATTEMPT, attempt.getId())) {
            return;
        }
        creditLedgerService.issueCredit(
                order.getPayerUserId(),
                CreditOriginType.PAYMENT_SURPLUS,
                LedgerSourceType.PAYMENT_ATTEMPT,
                attempt.getId(),
                PricingPolicy.toScoin(surplusAmount, paymentProperties),
                "Hoàn tiền thanh toán dư cho order " + order.getOrderCode()
        );
        log.info("Issued payment surplus credit for attempt {} order {} amount {} SCoin",
                attempt.getId(), order.getOrderCode(), surplusAmount);
    }

    public void markAttemptFinalState(PaymentAttempt attempt,
                                      PaymentAttemptStatus status,
                                      String providerTransactionId,
                                      String providerEventId,
                                      String providerStatus,
                                      String failureReason) {
        if (attempt == null) {
            return;
        }
        attempt.setStatus(status);
        attempt.setProviderTransactionId(providerTransactionId);
        attempt.setProviderEventId(providerEventId);
        attempt.setProviderStatus(providerStatus);
        attempt.setFailureReason(failureReason);
        paymentAttemptRepository.save(attempt);
    }

    private boolean isPaidProviderWebhook(String providerStatus) {
        if (!StringUtils.hasText(providerStatus)) {
            return false;
        }
        String normalized = providerStatus.trim().toUpperCase(Locale.ROOT);
        return "00".equals(normalized) || "PAID".equals(normalized) || "SUCCESS".equals(normalized);
    }

    private boolean isTerminalProviderWebhook(String providerStatus) {
        if (!StringUtils.hasText(providerStatus)) {
            return false;
        }
        return switch (providerStatus.trim().toUpperCase(Locale.ROOT)) {
            case "CANCELLED", "EXPIRED", "FAILED" -> true;
            default -> false;
        };
    }

    void applyTerminalWebhook(PaymentOrder order,
                              PaymentAttempt attempt,
                              PaymentGatewayProvider.VerifiedWebhook verified,
                              String providerEventId) {
        String status = verified.providerStatus().trim().toUpperCase(Locale.ROOT);
        order.setProviderOrderCode(verified.providerOrderCode());
        order.setProviderPaymentLinkId(verified.providerPaymentLinkId());
        order.setProviderTransactionId(verified.providerTransactionId());
        order.setProviderEventId(providerEventId);
        order.setProviderStatus(status);

        attempt.setProviderOrderCode(verified.providerOrderCode());
        attempt.setProviderPaymentLinkId(verified.providerPaymentLinkId());
        paymentLifecycleService.rollbackReservedCredit(order);
        if (couponService != null) {
            couponService.voidRedemption(order.getId());
        }
        Instant receivedAtUtc = timeProvider.instant();
        switch (status) {
            case "CANCELLED" -> {
                order.setStatus(PaymentOrderStatus.CANCELLED);
                order.setCancelledAtUtc(receivedAtUtc);
                order.setCancelledAt(BusinessTime.fromInstant(receivedAtUtc));
                markAttemptFinalState(attempt, PaymentAttemptStatus.CANCELLED,
                        verified.providerTransactionId(), providerEventId, status, "PayOS payment link bị hủy");
            }
            case "EXPIRED" -> {
                order.setStatus(PaymentOrderStatus.EXPIRED);
                order.setFailedAtUtc(receivedAtUtc);
                order.setFailedAt(BusinessTime.fromInstant(receivedAtUtc));
                markAttemptFinalState(attempt, PaymentAttemptStatus.EXPIRED,
                        verified.providerTransactionId(), providerEventId, status, "PayOS payment link đã hết hạn");
            }
            case "FAILED" -> {
                order.setStatus(PaymentOrderStatus.FAILED);
                order.setFailedAtUtc(receivedAtUtc);
                order.setFailedAt(BusinessTime.fromInstant(receivedAtUtc));
                markAttemptFinalState(attempt, PaymentAttemptStatus.FAILED,
                        verified.providerTransactionId(), providerEventId, status, "PayOS payment link thất bại");
            }
            default -> throw new IllegalArgumentException("Unsupported terminal payment status: " + status);
        }
    }

    private String resolveProviderEventId(PaymentGatewayProvider.VerifiedWebhook verified) {
        if (StringUtils.hasText(verified.providerEventId())) {
            return verified.providerEventId();
        }
        if (StringUtils.hasText(verified.providerPaymentLinkId())) {
            return verified.providerPaymentLinkId() + ":" + verified.providerOrderCode();
        }
        return verified.providerOrderCode();
    }

    private long parseProviderOrderCode(String providerOrderCode) {
        try {
            return Long.parseLong(providerOrderCode);
        } catch (NumberFormatException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "providerOrderCode PayOS hiện tại không hợp lệ");
        }
    }
}
