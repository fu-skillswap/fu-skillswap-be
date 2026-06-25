package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private final BookingRepository bookingRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CouponService couponService;
    private final CreditLedgerService creditLedgerService;
    private final PaymentProperties paymentProperties;

    @Transactional
    public PaymentCheckoutResponse checkout(UUID currentUserId, PaymentCheckoutRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || request.bookingId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "bookingId không được để trống");
        }

        Booking booking = bookingRepository.findByIdForSessionUpdate(request.bookingId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getMentee() == null || !currentUserId.equals(booking.getMentee().getId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentee của booking mới có thể thanh toán");
        }
        if (booking.getMentorProfile() == null || booking.getMentorProfile().getUserId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking không gắn với mentor hợp lệ");
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED && booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION
                && booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa sẵn sàng để thanh toán");
        }

        PaymentOrder existingOrder = paymentOrderRepository.findByBookingId(booking.getId()).orElse(null);
        if (existingOrder != null) {
            return toResponse(existingOrder);
        }

        int grossScoin = booking.getServicePriceScoinSnapshot() != null
                ? booking.getServicePriceScoinSnapshot()
                : (booking.getService() != null && booking.getService().getPriceScoin() != null ? booking.getService().getPriceScoin() : 0);
        grossScoin = Math.max(0, grossScoin);

        var coupon = couponService.resolveCoupon(request.couponCode());
        couponService.validateApplicable(coupon, booking, currentUserId, grossScoin);
        int couponDiscountScoin = couponService.calculateCouponDiscount(coupon, grossScoin);
        int netAfterCoupon = Math.max(0, grossScoin - couponDiscountScoin);

        UUID paymentOrderId = UUID.randomUUID();
        PaymentOrder draftOrder = PaymentOrder.builder()
                .id(paymentOrderId)
                .orderCode(generateOrderCode(paymentOrderId))
                .bookingId(booking.getId())
                .payerUserId(currentUserId)
                .mentorUserId(booking.getMentorProfile().getUserId())
                .serviceId(booking.getService() == null ? null : booking.getService().getId())
                .grossScoin(grossScoin)
                .couponId(coupon == null ? null : coupon.getId())
                .couponCodeSnapshot(coupon == null ? null : coupon.getCode())
                .couponDiscountScoin(couponDiscountScoin)
                .campaignCreditScoin(0)
                .userCreditScoin(0)
                .remainingPayableScoin(netAfterCoupon)
                .status(netAfterCoupon > 0 ? PaymentOrderStatus.PENDING : PaymentOrderStatus.PAID)
                .paymentProvider(PaymentProvider.PAYOS)
                .providerOrderCode(generateProviderOrderCode(paymentOrderId))
                .expiresAt(DateTimeUtil.now().plusMinutes(paymentProperties.getPaymentLinkExpiryMinutes()))
                .build();

        if (netAfterCoupon > 0) {
            var reservedEntries = creditLedgerService.reserveCredit(
                    currentUserId,
                    netAfterCoupon,
                    LedgerSourceType.PAYMENT_ORDER,
                    draftOrder.getId(),
                    List.of(CreditOriginType.CAMPAIGN_BONUS, CreditOriginType.COUPON_BONUS, CreditOriginType.REFUND, CreditOriginType.MANUAL),
                    "Reserve credit for payment order " + draftOrder.getOrderCode()
            );
            int campaignCredit = reservedEntries.stream()
                    .filter(entry -> entry.getOriginType() == CreditOriginType.CAMPAIGN_BONUS)
                    .mapToInt(entry -> entry.getAmountScoin() == null ? 0 : entry.getAmountScoin())
                    .sum();
            int userCredit = reservedEntries.stream()
                    .filter(entry -> entry.getOriginType() != CreditOriginType.CAMPAIGN_BONUS)
                    .mapToInt(entry -> entry.getAmountScoin() == null ? 0 : entry.getAmountScoin())
                    .sum();
            draftOrder.setCampaignCreditScoin(campaignCredit);
            draftOrder.setUserCreditScoin(userCredit);
            draftOrder.setRemainingPayableScoin(Math.max(0, netAfterCoupon - campaignCredit - userCredit));
            draftOrder.setPaymentLink(buildPaymentLink(draftOrder));
            draftOrder.setStatus(draftOrder.getRemainingPayableScoin() > 0 ? PaymentOrderStatus.PENDING : PaymentOrderStatus.PAID);
        }

        PaymentOrder savedOrder = paymentOrderRepository.save(draftOrder);
        paymentAttemptRepository.save(com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt.builder()
                .paymentOrderId(savedOrder.getId())
                .attemptNo(1)
                .status(savedOrder.getStatus() == PaymentOrderStatus.PAID
                        ? com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus.PAID
                        : com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus.PENDING)
                .providerOrderCode(savedOrder.getProviderOrderCode())
                .checkoutUrl(savedOrder.getPaymentLink())
                .build());
        if (coupon != null) {
            couponService.reserveCoupon(coupon, savedOrder.getId(), currentUserId, couponDiscountScoin);
            if (savedOrder.getStatus() == PaymentOrderStatus.PAID) {
                couponService.markRedeemed(savedOrder.getId());
            }
        }
        return toResponse(savedOrder);
    }

    @Transactional
    public PaymentCheckoutResponse handleWebhook(PaymentWebhookRequest request) {
        if (request == null || request.providerOrderCode() == null || request.providerOrderCode().isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "providerOrderCode không được để trống");
        }
        PaymentOrder order = paymentOrderRepository.findByProviderOrderCode(request.providerOrderCode())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));

        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return toResponse(order);
        }

        order.setProviderTransactionId(request.providerTransactionId());
        switch (request.status()) {
            case PAID -> {
                order.setStatus(PaymentOrderStatus.PAID);
                order.setPaidAt(request.paidAt() == null ? DateTimeUtil.now() : request.paidAt());
                couponService.markRedeemed(order.getId());
                paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(order.getId()).ifPresent(attempt -> {
                    attempt.setStatus(com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus.PAID);
                    attempt.setProviderTransactionId(request.providerTransactionId());
                    paymentAttemptRepository.save(attempt);
                });
            }
            case FAILED -> {
                order.setStatus(PaymentOrderStatus.FAILED);
                order.setFailedAt(DateTimeUtil.now());
                rollbackReservedCredit(order);
                couponService.voidRedemption(order.getId());
                markLatestAttemptFailed(order.getId(), request.providerTransactionId(), request.failureReason());
            }
            case CANCELLED -> {
                order.setStatus(PaymentOrderStatus.CANCELLED);
                order.setCancelledAt(DateTimeUtil.now());
                rollbackReservedCredit(order);
                couponService.voidRedemption(order.getId());
                markLatestAttemptFailed(order.getId(), request.providerTransactionId(), request.failureReason());
            }
            case EXPIRED -> {
                order.setStatus(PaymentOrderStatus.EXPIRED);
                order.setFailedAt(DateTimeUtil.now());
                rollbackReservedCredit(order);
                couponService.voidRedemption(order.getId());
                markLatestAttemptFailed(order.getId(), request.providerTransactionId(), request.failureReason());
            }
            default -> throw new BaseException(ErrorCode.BAD_REQUEST, "Trạng thái payment webhook không hợp lệ");
        }
        return toResponse(paymentOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutResponse getByBookingId(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        PaymentOrder order = paymentOrderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
        if (!currentUserId.equals(order.getPayerUserId()) && !currentUserId.equals(order.getMentorUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Không có quyền xem payment order này");
        }
        return toResponse(order);
    }

    private void rollbackReservedCredit(PaymentOrder order) {
        if (order.getRemainingPayableScoin() == null) {
            return;
        }
        creditLedgerService.releaseReservedCredit(
                order.getPayerUserId(),
                LedgerSourceType.PAYMENT_ORDER,
                order.getId(),
                "Rollback reserved credit for payment order " + order.getOrderCode()
        );
    }

    private void markLatestAttemptFailed(UUID paymentOrderId, String providerTransactionId, String failureReason) {
        paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(paymentOrderId).ifPresent(attempt -> {
            attempt.setStatus(com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus.FAILED);
            attempt.setProviderTransactionId(providerTransactionId);
            attempt.setFailureReason(failureReason);
            paymentAttemptRepository.save(attempt);
        });
    }

    private PaymentCheckoutResponse toResponse(PaymentOrder order) {
        return PaymentCheckoutResponse.builder()
                .paymentOrderId(order.getId())
                .orderCode(order.getOrderCode())
                .bookingId(order.getBookingId())
                .grossScoin(order.getGrossScoin())
                .couponDiscountScoin(order.getCouponDiscountScoin())
                .campaignCreditScoin(order.getCampaignCreditScoin())
                .userCreditScoin(order.getUserCreditScoin())
                .remainingPayableScoin(order.getRemainingPayableScoin())
                .status(order.getStatus())
                .paymentProvider(order.getPaymentProvider())
                .paymentLink(order.getPaymentLink())
                .expiresAt(order.getExpiresAt())
                .build();
    }

    private String generateOrderCode(UUID id) {
        return "PAY-" + id.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String generateProviderOrderCode(UUID id) {
        return "PAYOS-" + id.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String buildPaymentLink(PaymentOrder order) {
        return UriComponentsBuilder.fromUriString(paymentProperties.getPayos().getCheckoutBaseUrl())
                .queryParam("orderCode", order.getOrderCode())
                .queryParam("amount", order.getRemainingPayableScoin())
                .queryParam("returnUrl", paymentProperties.getPayos().getReturnUrl())
                .queryParam("cancelUrl", paymentProperties.getPayos().getCancelUrl())
                .toUriString();
    }
}
