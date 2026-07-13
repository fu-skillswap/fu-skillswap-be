package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentSettlementStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.integration.payos.PayOsGateway;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.session.service.SessionService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private static final long PAYOS_MAX_SAFE_ORDER_CODE = 9_007_199_254_740_991L;
    private static final int MIN_SERVICE_PRICE_SCOIN_PER_MINUTE = 1_200;
    private static final int MAX_SERVICE_PRICE_SCOIN_PER_MINUTE = 500_000;
    private static final long PROVIDER_ORDER_CODE_EPOCH_MILLIS = LocalDateTime.of(2025, 1, 1, 0, 0)
            .atZone(ZoneId.of(DateTimeUtil.ZONE_HCM))
            .toInstant()
            .toEpochMilli();
    private static final int PROVIDER_ORDER_CODE_SEQUENCE_MOD = 10_000;
    private static final AtomicLong PROVIDER_ORDER_CODE_LAST_BUCKET = new AtomicLong(-1L);
    private static final AtomicInteger PROVIDER_ORDER_CODE_SEQUENCE = new AtomicInteger(0);

    private final BookingRepository bookingRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CouponService couponService;
    private final CreditLedgerService creditLedgerService;
    private final CampaignService campaignService;
    private final PaymentProperties paymentProperties;
    private final PayOsGateway payOsGateway;
    private final SettlementService settlementService;
    private final SessionService sessionService;
    private final com.fptu.exe.skillswap.modules.conversation.service.ConversationService conversationService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public PaymentCheckoutResponse checkout(UUID currentUserId, PaymentCheckoutRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || request.bookingId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "bookingId không được để trống");
        }

        PaymentOrder savedOrder = transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findByIdForSessionUpdate(request.bookingId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
            validateCheckoutOwnership(currentUserId, booking);

            if (Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot())
                    || (booking.getServicePriceScoinSnapshot() != null && booking.getServicePriceScoinSnapshot() == 0)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Không cần thanh toán cho dịch vụ miễn phí");
            }

            PaymentOrder existingOrder = paymentOrderRepository.findByBookingId(booking.getId()).orElse(null);
            PaymentAttempt latestAttempt = existingOrder == null
                    ? null
                    : paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(existingOrder.getId()).orElse(null);
            if (existingOrder != null && latestAttempt != null) {
                trySynchronizeProviderStatus(existingOrder, latestAttempt);
            }

            if (existingOrder != null && existingOrder.getStatus() == PaymentOrderStatus.PAID) {
                return existingOrder;
            }
            if (existingOrder != null && isAwaitingPayment(existingOrder.getStatus()) && !isExpired(existingOrder)) {
                return existingOrder;
            }

            int originalPriceScoin = resolveBasePriceScoin(booking);
            int menteeSurchargeBps = paymentProperties.getMenteeSurchargeBps();
            int menteeSurchargeScoin = originalPriceScoin == 0 ? 0 : (originalPriceScoin * menteeSurchargeBps) / 10_000;
            int menteePayablePrice = originalPriceScoin + menteeSurchargeScoin;

            var coupon = couponService.resolveCoupon(request.couponCode());
            couponService.validateApplicable(coupon, booking, currentUserId, menteePayablePrice);
            int couponDiscountScoin = couponService.calculateCouponDiscount(coupon, menteePayablePrice);
            int amountAfterCoupon = Math.max(0, menteePayablePrice - couponDiscountScoin);

            CampaignService.CampaignCreditApplication campaignApplication =
                    campaignService.resolveCampaignCredit(currentUserId, booking, amountAfterCoupon);
            int campaignCreditAppliedScoin = Math.max(0, Math.min(amountAfterCoupon, campaignApplication.appliedScoin()));
            int amountAfterCampaign = Math.max(0, amountAfterCoupon - campaignCreditAppliedScoin);

            PaymentOrder draftOrder = existingOrder != null ? existingOrder : new PaymentOrder();
            prepareOrderForCheckout(draftOrder, booking, currentUserId, coupon, campaignApplication,
                    originalPriceScoin, menteePayablePrice, couponDiscountScoin, campaignCreditAppliedScoin);

            if (amountAfterCampaign > 0) {
                int userCredit = reserveUserCredit(currentUserId, draftOrder, amountAfterCampaign);
                draftOrder.setUserCreditScoin(userCredit);
                draftOrder.setRemainingPayableScoin(Math.max(0, amountAfterCampaign - userCredit));
                draftOrder.setStatus(draftOrder.getRemainingPayableScoin() > 0
                        ? hasInternalCoverage(menteePayablePrice, couponDiscountScoin, campaignCreditAppliedScoin, userCredit)
                            ? PaymentOrderStatus.PARTIALLY_COVERED_BY_CREDIT
                            : PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT
                        : PaymentOrderStatus.PAID);
                if (draftOrder.getStatus() == PaymentOrderStatus.PAID) {
                    draftOrder.setSettlementStatus(PaymentSettlementStatus.HELD);
                }
            } else {
                draftOrder.setUserCreditScoin(0);
                draftOrder.setRemainingPayableScoin(0);
                draftOrder.setStatus(PaymentOrderStatus.PAID);
                draftOrder.setSettlementStatus(PaymentSettlementStatus.HELD);
            }

            return paymentOrderRepository.save(draftOrder);
        });

        // Fast path for paid or existing awaiting payment
        if (savedOrder.getStatus() == PaymentOrderStatus.PAID) {
            if (savedOrder.getProviderOrderCode() == null) {
                return finalizeFullyPaidOrder(savedOrder);
            }
            PaymentAttempt latestAttempt = paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(savedOrder.getId()).orElse(null);
            return toResponse(savedOrder, latestAttempt);
        }
        if (isAwaitingPayment(savedOrder.getStatus()) && savedOrder.getProviderOrderCode() != null) {
            PaymentAttempt latestAttempt = paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(savedOrder.getId()).orElse(null);
            return toResponse(savedOrder, latestAttempt);
        }

        int nextAttemptNo = (int) paymentAttemptRepository.countByPaymentOrderId(savedOrder.getId()) + 1;
        long providerOrderCode = savedOrder.getRemainingPayableScoin() > 0 ? generateProviderOrderCode(savedOrder.getId(), nextAttemptNo) : 0;
        
        PayOsGateway.CreatePaymentLinkResult createResult = null;
        if (savedOrder.getRemainingPayableScoin() > 0) {
            Booking booking = bookingRepository.findById(request.bookingId()).orElseThrow();
            createResult = payOsGateway.createPaymentLink(
                    buildCreatePaymentLinkCommand(booking, savedOrder, providerOrderCode)
            );
        }

        final PayOsGateway.CreatePaymentLinkResult finalCreateResult = createResult;
        return transactionTemplate.execute(status -> {
            PaymentOrder orderToUpdate = paymentOrderRepository.findByIdForUpdate(savedOrder.getId()).orElseThrow();
            PaymentAttempt attempt;
            if (orderToUpdate.getRemainingPayableScoin() > 0) {
                orderToUpdate.setProviderOrderCode(finalCreateResult.providerOrderCode());
                orderToUpdate.setProviderPaymentLinkId(finalCreateResult.providerPaymentLinkId());
                orderToUpdate.setProviderStatus(finalCreateResult.providerStatus());
                orderToUpdate.setPaymentLink(finalCreateResult.checkoutUrl());
                orderToUpdate.setExpiresAt(finalCreateResult.expiresAt());
                orderToUpdate = paymentOrderRepository.save(orderToUpdate);

                attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                        .paymentOrderId(orderToUpdate.getId())
                        .attemptNo(nextAttemptNo)
                        .status(PaymentAttemptStatus.REDIRECTED)
                        .providerOrderCode(finalCreateResult.providerOrderCode())
                        .providerPaymentLinkId(finalCreateResult.providerPaymentLinkId())
                        .providerStatus(finalCreateResult.providerStatus())
                        .checkoutUrl(finalCreateResult.checkoutUrl())
                        .build());
            } else {
                orderToUpdate.setProviderOrderCode(null);
                orderToUpdate.setProviderPaymentLinkId(null);
                orderToUpdate.setProviderStatus("PAID");
                orderToUpdate.setPaymentLink(null);
                orderToUpdate.setExpiresAt(null);
                orderToUpdate = paymentOrderRepository.save(orderToUpdate);
                attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                        .paymentOrderId(orderToUpdate.getId())
                        .attemptNo(nextAttemptNo)
                        .status(PaymentAttemptStatus.SUCCEEDED)
                        .providerStatus("PAID")
                        .build());
            }

            if (request.couponCode() != null) {
                Coupon coupon = couponService.resolveCoupon(request.couponCode());
                if (coupon != null) {
                    couponService.reserveCoupon(coupon, orderToUpdate.getId(), currentUserId, orderToUpdate.getCouponDiscountScoin());
                }
            }
            if (orderToUpdate.getStatus() == PaymentOrderStatus.PAID) {
                Booking lockedBooking = bookingRepository.findByIdForSessionUpdate(orderToUpdate.getBookingId()).orElseThrow();
                finalizeInternalPayment(orderToUpdate, attempt, null, null, "PAID", lockedBooking);
                orderToUpdate = paymentOrderRepository.save(orderToUpdate);
            }
            return toResponse(orderToUpdate, attempt);
        });
    }

    public PaymentCheckoutResponse handleWebhook(PaymentWebhookRequest request) {
        if (request == null || request.data() == null || request.data().orderCode() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Webhook PayOS thiếu data.orderCode");
        }

        PayOsGateway.VerifiedWebhook verified = payOsGateway.verifyWebhook(request);
        if (!verified.success() || !isPaidProviderWebhook(verified.providerStatus())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Webhook PayOS chưa xác nhận thanh toán thành công");
        }

        PaymentAttempt optimisticAttempt = paymentAttemptRepository.findByProviderOrderCode(verified.providerOrderCode())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment attempt"));
        if (optimisticAttempt.getStatus() == PaymentAttemptStatus.SUCCEEDED
                || optimisticAttempt.getStatus() == PaymentAttemptStatus.SUCCEEDED_SURPLUS) {
            PaymentOrder optimisticOrder = paymentOrderRepository.findById(optimisticAttempt.getPaymentOrderId()).orElseThrow();
            return toResponse(optimisticOrder, optimisticAttempt);
        }

        return transactionTemplate.execute(status -> {
            PaymentAttempt attempt = paymentAttemptRepository.findByProviderOrderCodeForUpdate(verified.providerOrderCode())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                            "Không tìm thấy payment attempt tương ứng với orderCode PayOS"));
            PaymentOrder order = paymentOrderRepository.findByIdForUpdate(attempt.getPaymentOrderId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
            Booking lockedBooking = bookingRepository.findByIdForSessionUpdate(order.getBookingId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để hoàn tất thanh toán"));

            String providerEventId = resolveProviderEventId(verified);
            if (StringUtils.hasText(providerEventId)
                    && (paymentOrderRepository.existsByProviderEventId(providerEventId)
                    || paymentAttemptRepository.existsByProviderEventId(providerEventId))) {
                return toResponse(order, attempt);
            }
            if (order.getStatus() == PaymentOrderStatus.PAID) {
                if (attempt.getStatus() != PaymentAttemptStatus.SUCCEEDED && attempt.getStatus() != PaymentAttemptStatus.SUCCEEDED_SURPLUS) {
                    attempt.setProviderOrderCode(verified.providerOrderCode());
                    attempt.setProviderPaymentLinkId(verified.providerPaymentLinkId());
                    attempt.setProviderStatus("PAID");
                    issueSurplusCreditIfNeeded(order, attempt, verified.amount(), 0L);
                    markAttemptFinalState(attempt, PaymentAttemptStatus.SUCCEEDED_SURPLUS, verified.providerTransactionId(), providerEventId, "PAID", null);
                }
                return toResponse(order, attempt);
            }

            validateProviderPaidAmount(order, verified.amount());
            order.setProviderOrderCode(verified.providerOrderCode());
            order.setProviderPaymentLinkId(verified.providerPaymentLinkId());
            order.setProviderStatus("PAID");
            order.setProviderTransactionId(verified.providerTransactionId());
            order.setProviderEventId(providerEventId);
            if (verified.paidAt() != null) {
                order.setPaidAt(verified.paidAt());
            }

            attempt.setProviderOrderCode(verified.providerOrderCode());
            attempt.setProviderPaymentLinkId(verified.providerPaymentLinkId());
            attempt.setProviderStatus("PAID");
            finalizeInternalPayment(order, attempt, verified.providerTransactionId(), providerEventId, "PAID", lockedBooking);
            issueSurplusCreditIfNeeded(order, attempt, verified.amount(), expectedProviderPayable(order));
            order = paymentOrderRepository.save(order);
            return toResponse(order, attempt);
        });
    }

    @Transactional
    public PaymentCheckoutResponse getByBookingId(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        PaymentOrder order = paymentOrderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
        if (!currentUserId.equals(order.getPayerUserId()) && !currentUserId.equals(order.getMentorUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Không có quyền xem payment order này");
        }

        PaymentAttempt latestAttempt = paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(order.getId()).orElse(null);
        if (latestAttempt != null) {
            trySynchronizeProviderStatus(order, latestAttempt);
        }
        return toResponse(order, latestAttempt);
    }

    @Transactional
    public void synchronizeProviderStatusForBooking(UUID bookingId) {
        if (bookingId == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByBookingId(bookingId).orElse(null);
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

    @Transactional
    public void reconcileStaleProviderPayments() {
        List<PaymentOrder> staleOrders = paymentOrderRepository.findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                List.of(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT, PaymentOrderStatus.PARTIALLY_COVERED_BY_CREDIT),
                DateTimeUtil.now().minusMinutes(15),
                PageRequest.of(0, 50)
        );
        for (PaymentOrder order : staleOrders) {
            try {
                synchronizeProviderStatusForBooking(order.getBookingId());
            } catch (RuntimeException ex) {
                log.warn("Failed to reconcile payment order {} for booking {}: {}", order.getId(), order.getBookingId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public void handleMenteeCancellation(Booking booking, boolean lateCancellation) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByBookingIdForUpdate(booking.getId()).orElse(null);
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
        settlementService.handlePaidBookingCancelledByMentee(booking, order, lateCancellation);
    }

    @Transactional
    public void handleMentorCancellation(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByBookingIdForUpdate(booking.getId()).orElse(null);
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
        settlementService.handlePaidBookingCancelledByMentor(booking, order);
    }

    private void validateCheckoutOwnership(UUID currentUserId, Booking booking) {
        if (booking.getMentee() == null || !currentUserId.equals(booking.getMentee().getId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentee của booking mới có thể thanh toán");
        }
        if (booking.getMentorProfile() == null || booking.getMentorProfile().getUserId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking không gắn với mentor hợp lệ");
        }
        if (booking.getStatus() == BookingStatus.PAID) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking này đã được thanh toán trước đó");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE
                || booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR
                || booking.getStatus() == BookingStatus.REJECTED
                || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Booking đã kết thúc ở trạng thái " + booking.getStatus() + " và không thể thanh toán");
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa sẵn sàng để thanh toán (trạng thái: " + booking.getStatus() + ")");
        }
    }

    private int resolveBasePriceScoin(Booking booking) {
        boolean isFree = Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot());
        int basePriceScoin = booking.getServicePriceScoinSnapshot() != null
                ? booking.getServicePriceScoinSnapshot()
                : (booking.getService() != null && booking.getService().getPriceScoin() != null
                ? booking.getService().getPriceScoin()
                : 0);
        if (isFree) {
            return 0;
        }
        Integer durationMinutes = booking.getServiceDurationSnapshot() != null
                ? booking.getServiceDurationSnapshot()
                : (booking.getService() == null ? null : booking.getService().getDurationMinutes());
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ mentoring đang có thời lượng không hợp lệ");
        }
        int normalizedPrice = Math.max(0, basePriceScoin);
        int minimumPrice = durationMinutes * MIN_SERVICE_PRICE_SCOIN_PER_MINUTE;
        if (normalizedPrice < minimumPrice) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Dịch vụ mentoring có phí phải có giá tối thiểu " + minimumPrice + " SCoin cho " + durationMinutes + " phút"
            );
        }
        int maximumPrice = durationMinutes * MAX_SERVICE_PRICE_SCOIN_PER_MINUTE;
        if (normalizedPrice > maximumPrice) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Dịch vụ mentoring có phí chỉ được đặt tối đa " + maximumPrice + " SCoin cho " + durationMinutes + " phút"
            );
        }
        return normalizedPrice;
    }

    private void cancelAwaitingPaymentOrder(PaymentOrder order) {
        if (order == null || isFinal(order.getStatus())) {
            return;
        }
        order.setStatus(PaymentOrderStatus.CANCELLED);
        order.setCancelledAt(DateTimeUtil.now());
        rollbackReservedCredit(order);
        couponService.voidRedemption(order.getId());
        paymentOrderRepository.save(order);
    }

    private void expireAwaitingPaymentOrder(PaymentOrder order) {
        if (order == null || isFinal(order.getStatus())) {
            return;
        }
        order.setStatus(PaymentOrderStatus.EXPIRED);
        order.setFailedAt(DateTimeUtil.now());
        rollbackReservedCredit(order);
        couponService.voidRedemption(order.getId());
        paymentOrderRepository.save(order);
    }

    @Transactional
    public void expireAwaitingPayment(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByBookingIdForUpdate(booking.getId()).orElse(null);
        if (order == null) {
            return;
        }
        if (isAwaitingPayment(order.getStatus())) {
            expireAwaitingPaymentOrder(order);
        }
    }

    private void prepareOrderForCheckout(PaymentOrder draftOrder,
                                         Booking booking,
                                         UUID currentUserId,
                                         Coupon coupon,
                                         CampaignService.CampaignCreditApplication campaignApplication,
                                         int originalPriceScoin,
                                         int menteePayablePrice,
                                         int couponDiscountScoin,
                                         int campaignCreditAppliedScoin) {
        UUID paymentOrderId = draftOrder.getId() != null ? draftOrder.getId() : UuidUtil.generateUuidV7();
        draftOrder.setId(paymentOrderId);
        if (!StringUtils.hasText(draftOrder.getOrderCode())) {
            draftOrder.setOrderCode(generateOrderCode(paymentOrderId));
        }
        draftOrder.setBookingId(booking.getId());
        draftOrder.setPayerUserId(currentUserId);
        draftOrder.setMentorUserId(booking.getMentorProfile().getUserId());
        draftOrder.setServiceId(booking.getService() == null ? null : booking.getService().getId());
        
        int menteeSurchargeBps = paymentProperties.getMenteeSurchargeBps();
        int mentorCommissionBps = paymentProperties.getMentorCommissionBps();

        int mentorCommissionScoin = originalPriceScoin == 0 ? 0 : (originalPriceScoin * mentorCommissionBps) / 10_000;
        int mentorNetScoin = Math.max(0, originalPriceScoin - mentorCommissionScoin);

        draftOrder.setGrossScoin(menteePayablePrice);
        draftOrder.setCommissionRateBps(menteeSurchargeBps + mentorCommissionBps);
        draftOrder.setMentorNetScoin(mentorNetScoin);
        draftOrder.setCommissionScoin(Math.max(0, menteePayablePrice - mentorNetScoin));

        if (coupon != null) {
            draftOrder.setCouponId(coupon.getId());
            draftOrder.setCouponCodeSnapshot(coupon.getCode());
        } else {
            draftOrder.setCouponId(null);
            draftOrder.setCouponCodeSnapshot(null);
        }
        draftOrder.setCouponDiscountScoin(couponDiscountScoin);
        draftOrder.setCampaignId(campaignApplication.campaignId());
        draftOrder.setCampaignNameSnapshot(campaignApplication.campaignName());
        draftOrder.setCampaignFundingSource(campaignApplication.fundingSource());
        draftOrder.setCampaignCreditScoin(campaignCreditAppliedScoin);
        draftOrder.setProviderTransactionId(null);
        draftOrder.setProviderEventId(null);
        draftOrder.setProviderPaymentLinkId(null);
        draftOrder.setProviderStatus(null);
        draftOrder.setPaymentLink(null);
        draftOrder.setPaidAt(null);
        draftOrder.setCancelledAt(null);
        draftOrder.setFailedAt(null);
        draftOrder.setCreditFinalizedAt(null);
        draftOrder.setExpiresAt(null);
    }

    private int reserveUserCredit(UUID currentUserId, PaymentOrder order, int amountAfterCampaign) {
        var balances = creditLedgerService.getAvailableBalanceByOrigin(currentUserId);
        int availableCredit = balances.entrySet().stream()
                .filter(e -> List.of(CreditOriginType.CAMPAIGN_BONUS, CreditOriginType.COUPON_BONUS, CreditOriginType.REFUND, CreditOriginType.MANUAL).contains(e.getKey()))
                .mapToInt(e -> e.getValue())
                .sum();
        int reserveAmount = Math.max(0, Math.min(amountAfterCampaign, availableCredit));
        if (reserveAmount <= 0) {
            return 0;
        }
        var reservedEntries = creditLedgerService.reserveCredit(
                currentUserId,
                reserveAmount,
                LedgerSourceType.PAYMENT_ORDER,
                order.getId(),
                List.of(CreditOriginType.CAMPAIGN_BONUS, CreditOriginType.COUPON_BONUS, CreditOriginType.REFUND, CreditOriginType.MANUAL),
                "Reserve credit for payment order " + order.getOrderCode()
        );
        return reservedEntries.stream()
                .mapToInt(entry -> entry.getAmountScoin() == null ? 0 : entry.getAmountScoin())
                .sum();
    }

    private PayOsGateway.CreatePaymentLinkCommand buildCreatePaymentLinkCommand(Booking booking,
                                                                                PaymentOrder order,
                                                                                long providerOrderCode) {
        return new PayOsGateway.CreatePaymentLinkCommand(
                providerOrderCode,
                order.getRemainingPayableScoin().longValue(),
                buildProviderDescription(order),
                paymentProperties.getPayos().getReturnUrl(),
                paymentProperties.getPayos().getCancelUrl(),
                DateTimeUtil.now().plusMinutes(paymentProperties.getPaymentLinkExpiryMinutes())
                        .atZone(java.time.ZoneId.of(DateTimeUtil.ZONE_HCM))
                        .toEpochSecond(),
                booking.getMentee() == null ? null : booking.getMentee().getFullName(),
                booking.getMentee() == null ? null : booking.getMentee().getEmail(),
                null,
                List.of(new PayOsGateway.PaymentItem(
                        buildPaymentItemName(booking),
                        1,
                        order.getRemainingPayableScoin().longValue()
                ))
        );
    }

    private String buildProviderDescription(PaymentOrder order) {
        String seed = order.getOrderCode() == null ? "SkillSwap" : order.getOrderCode().replaceAll("[^A-Za-z0-9]", "");
        String description = "SkillSwap" + seed;
        return description.length() > 25 ? description.substring(0, 25) : description;
    }

    private String buildPaymentItemName(Booking booking) {
        if (StringUtils.hasText(booking.getServiceTitleSnapshot())) {
            return booking.getServiceTitleSnapshot();
        }
        if (booking.getService() != null && StringUtils.hasText(booking.getService().getTitle())) {
            return booking.getService().getTitle();
        }
        return "SkillSwap mentoring session";
    }

    private void trySynchronizeProviderStatus(PaymentOrder order, PaymentAttempt attempt) {
        if (order == null || attempt == null || isFinal(order.getStatus()) || !StringUtils.hasText(attempt.getProviderOrderCode())) {
            return;
        }
        if (order.getRemainingPayableScoin() == null || order.getRemainingPayableScoin() <= 0) {
            return;
        }
        try {
            PayOsGateway.PaymentLinkDetails paymentLink = payOsGateway.getPaymentLink(parseProviderOrderCode(attempt.getProviderOrderCode()));
            order.setProviderStatus(paymentLink.providerStatus());
            order.setProviderPaymentLinkId(paymentLink.providerPaymentLinkId());
            attempt.setProviderStatus(paymentLink.providerStatus());
            attempt.setProviderPaymentLinkId(paymentLink.providerPaymentLinkId());

            String providerStatus = paymentLink.providerStatus() == null
                    ? ""
                    : paymentLink.providerStatus().toUpperCase(Locale.ROOT);
            switch (providerStatus) {
                case "PAID", "SUCCESS", "00" -> synchronizePaidProviderStatus(order, attempt, paymentLink);
                case "CANCELLED" -> {
                    if (!isFinal(order.getStatus())) {
                        order.setStatus(PaymentOrderStatus.CANCELLED);
                        order.setCancelledAt(paymentLink.cancelledAt() == null ? DateTimeUtil.now() : paymentLink.cancelledAt());
                        rollbackReservedCredit(order);
                        couponService.voidRedemption(order.getId());
                        markAttemptFinalState(attempt, PaymentAttemptStatus.CANCELLED, attempt.getProviderTransactionId(),
                                attempt.getProviderEventId(), providerStatus, "PayOS payment link bị hủy");
                    }
                }
                case "EXPIRED" -> {
                    if (!isFinal(order.getStatus())) {
                        order.setStatus(PaymentOrderStatus.EXPIRED);
                        order.setFailedAt(DateTimeUtil.now());
                        rollbackReservedCredit(order);
                        couponService.voidRedemption(order.getId());
                        markAttemptFinalState(attempt, PaymentAttemptStatus.EXPIRED, attempt.getProviderTransactionId(),
                                attempt.getProviderEventId(), providerStatus, "PayOS payment link đã hết hạn");
                    }
                }
                case "FAILED" -> {
                    if (!isFinal(order.getStatus())) {
                        order.setStatus(PaymentOrderStatus.FAILED);
                        order.setFailedAt(DateTimeUtil.now());
                        rollbackReservedCredit(order);
                        couponService.voidRedemption(order.getId());
                        markAttemptFinalState(attempt, PaymentAttemptStatus.FAILED, attempt.getProviderTransactionId(),
                                attempt.getProviderEventId(), providerStatus, "PayOS payment link thất bại");
                    }
                }
                default -> paymentAttemptRepository.save(attempt);
            }
            paymentOrderRepository.save(order);
        } catch (Exception ex) {
            log.warn("Không thể đồng bộ trạng thái PayOS cho paymentOrderId={}: {}", order.getId(), ex.getMessage());
        }
    }

    private long parseProviderOrderCode(String providerOrderCode) {
        try {
            return Long.parseLong(providerOrderCode);
        } catch (NumberFormatException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "providerOrderCode PayOS hiện tại không hợp lệ");
        }
    }

    private void rollbackReservedCredit(PaymentOrder order) {
        creditLedgerService.releaseReservedCredit(
                order.getPayerUserId(),
                LedgerSourceType.PAYMENT_ORDER,
                order.getId(),
                "Rollback reserved credit for payment order " + order.getOrderCode()
        );
    }

    private void synchronizePaidProviderStatus(PaymentOrder order,
                                               PaymentAttempt attempt,
                                               PayOsGateway.PaymentLinkDetails paymentLink) {
        PaymentAttempt lockedAttempt = paymentAttemptRepository.findByProviderOrderCodeForUpdate(attempt.getProviderOrderCode())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy payment attempt tương ứng với orderCode PayOS"));
        PaymentOrder lockedOrder = paymentOrderRepository.findByIdForUpdate(lockedAttempt.getPaymentOrderId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
        Booking lockedBooking = bookingRepository.findByIdForSessionUpdate(lockedOrder.getBookingId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để hoàn tất thanh toán"));

        lockedOrder.setProviderOrderCode(lockedAttempt.getProviderOrderCode());
        lockedOrder.setProviderPaymentLinkId(paymentLink.providerPaymentLinkId());
        lockedOrder.setProviderStatus(paymentLink.providerStatus());
        lockedAttempt.setProviderPaymentLinkId(paymentLink.providerPaymentLinkId());
        lockedAttempt.setProviderStatus(paymentLink.providerStatus());

        if (lockedOrder.getStatus() != PaymentOrderStatus.PAID) {
            finalizeInternalPayment(lockedOrder, lockedAttempt, lockedAttempt.getProviderTransactionId(),
                    lockedAttempt.getProviderEventId(), paymentLink.providerStatus(), lockedBooking);
            paymentOrderRepository.save(lockedOrder);
        } else {
            paymentAttemptRepository.save(lockedAttempt);
        }

        copyOrderState(order, lockedOrder);
        copyAttemptState(attempt, lockedAttempt);
    }

    private void finalizeInternalPayment(PaymentOrder order,
                                         PaymentAttempt attempt,
                                         String providerTransactionId,
                                         String providerEventId,
                                         String providerStatus,
                                         Booking lockedBooking) {
        if (order.getCreditFinalizedAt() == null) {
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
            order.setCreditFinalizedAt(DateTimeUtil.now());
        }
        couponService.markRedeemed(order.getId());
        order.setStatus(PaymentOrderStatus.PAID);
        order.setSettlementStatus(PaymentSettlementStatus.HELD);
        order.setProviderTransactionId(providerTransactionId);
        order.setProviderEventId(providerEventId);
        order.setProviderStatus(providerStatus);
        order.setPaidAt(order.getPaidAt() == null ? DateTimeUtil.now() : order.getPaidAt());
        markAttemptFinalState(attempt, PaymentAttemptStatus.SUCCEEDED, providerTransactionId, providerEventId, providerStatus, null);
        finalizePaidBooking(order, lockedBooking);
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
                : Math.max(0L, order.getRemainingPayableScoin().longValue());
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
        if (surplusAmount > Integer.MAX_VALUE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số tiền thanh toán dư vượt quá giới hạn hệ thống");
        }
        if (creditLedgerService.hasIssuedCreditForSource(LedgerSourceType.PAYMENT_ATTEMPT, attempt.getId())) {
            return;
        }
        creditLedgerService.issueCredit(
                order.getPayerUserId(),
                CreditOriginType.PAYMENT_SURPLUS,
                LedgerSourceType.PAYMENT_ATTEMPT,
                attempt.getId(),
                (int) surplusAmount,
                "Hoàn tiền thanh toán dư cho order " + order.getOrderCode()
        );
        log.info("Issued payment surplus credit for attempt {} order {} amount {} SCoin",
                attempt.getId(), order.getOrderCode(), surplusAmount);
    }

    private void finalizePaidBooking(PaymentOrder order, Booking lockedBooking) {
        Booking booking = lockedBooking != null
                ? lockedBooking
                : bookingRepository.findByIdForSessionUpdate(order.getBookingId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để hoàn tất thanh toán"));
        if (booking.getStatus() == BookingStatus.PAID) {
            sessionService.createForAcceptedBooking(booking);
            conversationService.createDirectForAcceptedBooking(booking);
            return;
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                && booking.getStatus() != BookingStatus.ACCEPTED) {
            // Booking đã bị huỷ/hết hạn trước khi webhook đến — ghi log cảnh báo nhưng không throw
            // để tránh rollback transaction và khiến PayOS retry lặp vô tận.
            compensateCapturedPaymentForTerminalBooking(booking, order);
            log.warn("finalizePaidBooking: booking {} ở trạng thái {} không thể chuyển sang PAID. " +
                            "Payment order vẫn được ghi nhận PAID và hệ thống đã chạy bù trừ nội bộ nếu cần.",
                    booking.getId(), booking.getStatus());
            return;
        }
        booking.setStatus(BookingStatus.PAID);
        bookingRepository.save(booking);
        internalTelemetryService.record(
                "BOOKING_PAYMENT_CONFIRMED",
                booking.getMentee() == null ? null : booking.getMentee().getId(),
                "BOOKING",
                booking.getId(),
                java.util.Map.of(
                        "mentorUserId", String.valueOf(booking.getMentorProfile() == null ? null : booking.getMentorProfile().getUserId()),
                        "grossScoin", String.valueOf(order.getGrossScoin()),
                        "remainingPayableScoin", String.valueOf(order.getRemainingPayableScoin())
                )
        );

        sessionService.createForAcceptedBooking(booking);
        conversationService.createDirectForAcceptedBooking(booking);

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                booking.getId(),
                booking.getMentee().getId(),
                booking.getMentorProfile().getUserId(),
                booking.getStatus(),
                "Thanh toán thành công. Lịch học đã được xác nhận.",
                booking.getUpdatedAt() != null ? booking.getUpdatedAt() : com.fptu.exe.skillswap.shared.util.DateTimeUtil.now()
        ));

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                booking.getMentorProfile().getUserId(),
                com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_PAYMENT_CONFIRMED,
                "Mentee đã hoàn tất thanh toán và lịch đã được xác nhận",
                booking.getMentee().getFullName() + " đã hoàn tất thanh toán cho lịch mentoring với bạn.",
                "BOOKING",
                booking.getId()
        ));

        eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                .bookingId(booking.getId())
                .eventType(BookingEmailNotificationEvent.EventType.BOOKING_PAID_CONFIRMED_EMAIL)
                .recipientEmail(booking.getMentorProfile().getUser().getEmail())
                .recipientName(booking.getMentorProfile().getUser().getFullName())
                .actorName(booking.getMentee().getFullName())
                .bookingStartTime(booking.getSelectedStartTime())
                .bookingEndTime(booking.getSelectedEndTime())
                .learningGoalTitle(booking.getLearningGoalTitle())
                .learningGoalDescription(booking.getLearningGoalDescription())
                .serviceTitle(booking.getServiceTitleSnapshot())
                .serviceDurationMinutes(booking.getServiceDurationSnapshot())
                .serviceFree(booking.getServiceIsFreeSnapshot())
                .servicePriceScoin(booking.getServicePriceScoinSnapshot())
                .serviceExpectedOutcome(booking.getServiceExpectedOutcomeSnapshot())
                .mentorResponseNote(booking.getMentorResponseNote())
                .createdAt(DateTimeUtil.now())
                .build());
    }

    private PaymentCheckoutResponse finalizeFullyPaidOrder(PaymentOrder savedOrder) {
        Booking lockedBooking = bookingRepository.findByIdForSessionUpdate(savedOrder.getBookingId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để hoàn tất thanh toán"));
        if (savedOrder.getCreditFinalizedAt() == null) {
            savedOrder.setCreditFinalizedAt(DateTimeUtil.now());
            savedOrder = paymentOrderRepository.save(savedOrder);
        }
        finalizePaidBooking(savedOrder, lockedBooking);
        PaymentAttempt latestAttempt = paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(savedOrder.getId()).orElse(null);
        return toResponse(savedOrder, latestAttempt);
    }

    private void compensateCapturedPaymentForTerminalBooking(Booking booking, PaymentOrder order) {
        if (booking == null || order == null) {
            return;
        }
        switch (booking.getStatus()) {
            case CANCELLED_BY_MENTEE -> settlementService.handlePaidBookingCancelledByMentee(booking, order, false);
            case CANCELLED_BY_MENTOR, REJECTED, EXPIRED -> settlementService.handlePaidBookingCancelledByMentor(booking, order);
            default -> {
            }
        }
    }

    private void markAttemptFinalState(PaymentAttempt attempt,
                                       PaymentAttemptStatus status,
                                       String providerTransactionId,
                                       String providerEventId,
                                       String providerStatus,
                                       String failureReason) {
        attempt.setStatus(status);
        attempt.setProviderTransactionId(providerTransactionId);
        attempt.setProviderEventId(providerEventId);
        attempt.setProviderStatus(providerStatus);
        attempt.setFailureReason(failureReason);
        paymentAttemptRepository.save(attempt);
    }

    private boolean isAwaitingPayment(PaymentOrderStatus status) {
        return status == PaymentOrderStatus.PENDING
                || status == PaymentOrderStatus.PARTIALLY_COVERED_BY_CREDIT
                || status == PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT;
    }

    private boolean hasInternalCoverage(int basePriceScoin, int couponDiscountScoin, int campaignCreditAppliedScoin, int userCreditAppliedScoin) {
        return couponDiscountScoin > 0
                || campaignCreditAppliedScoin > 0
                || userCreditAppliedScoin > 0
                || basePriceScoin == 0;
    }

    private boolean isFinal(PaymentOrderStatus status) {
        return status == PaymentOrderStatus.PAID
                || status == PaymentOrderStatus.FAILED
                || status == PaymentOrderStatus.CANCELLED
                || status == PaymentOrderStatus.EXPIRED;
    }

    private boolean isExpired(PaymentOrder order) {
        return order.getExpiresAt() != null && order.getExpiresAt().isBefore(DateTimeUtil.now());
    }

    private boolean isPaidProviderWebhook(String providerStatus) {
        if (!StringUtils.hasText(providerStatus)) {
            return false;
        }
        String normalized = providerStatus.trim().toUpperCase(Locale.ROOT);
        return "00".equals(normalized) || "PAID".equals(normalized);
    }

    private String resolveProviderEventId(PayOsGateway.VerifiedWebhook verified) {
        if (StringUtils.hasText(verified.providerEventId())) {
            return verified.providerEventId();
        }
        if (StringUtils.hasText(verified.providerPaymentLinkId())) {
            return verified.providerPaymentLinkId() + ":" + verified.providerOrderCode();
        }
        return verified.providerOrderCode();
    }

    private void copyOrderState(PaymentOrder target, PaymentOrder source) {
        target.setStatus(source.getStatus());
        target.setProviderOrderCode(source.getProviderOrderCode());
        target.setProviderPaymentLinkId(source.getProviderPaymentLinkId());
        target.setProviderStatus(source.getProviderStatus());
        target.setProviderTransactionId(source.getProviderTransactionId());
        target.setProviderEventId(source.getProviderEventId());
        target.setPaidAt(source.getPaidAt());
        target.setCancelledAt(source.getCancelledAt());
        target.setFailedAt(source.getFailedAt());
        target.setCreditFinalizedAt(source.getCreditFinalizedAt());
    }

    private void copyAttemptState(PaymentAttempt target, PaymentAttempt source) {
        target.setStatus(source.getStatus());
        target.setProviderOrderCode(source.getProviderOrderCode());
        target.setProviderPaymentLinkId(source.getProviderPaymentLinkId());
        target.setProviderTransactionId(source.getProviderTransactionId());
        target.setProviderEventId(source.getProviderEventId());
        target.setProviderStatus(source.getProviderStatus());
        target.setFailureReason(source.getFailureReason());
    }

    private PaymentCheckoutResponse toResponse(PaymentOrder order, PaymentAttempt attempt) {
        return PaymentCheckoutResponse.builder()
                .paymentOrderId(order.getId())
                .orderCode(order.getOrderCode())
                .bookingId(order.getBookingId())
                .attemptNo(attempt == null ? null : attempt.getAttemptNo())
                .basePriceScoin(order.getGrossScoin())
                .couponDiscountScoin(order.getCouponDiscountScoin())
                .campaignCreditAppliedScoin(order.getCampaignCreditScoin())
                .userCreditAppliedScoin(order.getUserCreditScoin())
                .remainingPayableScoin(order.getRemainingPayableScoin())
                .remainingPayableVnd(order.getRemainingPayableScoin())
                .status(order.getStatus())
                .paymentProvider(order.getPaymentProvider())
                .providerOrderCode(attempt != null && StringUtils.hasText(attempt.getProviderOrderCode())
                        ? attempt.getProviderOrderCode()
                        : order.getProviderOrderCode())
                .providerPaymentLinkId(attempt != null && StringUtils.hasText(attempt.getProviderPaymentLinkId())
                        ? attempt.getProviderPaymentLinkId()
                        : order.getProviderPaymentLinkId())
                .providerStatus(attempt != null && StringUtils.hasText(attempt.getProviderStatus())
                        ? attempt.getProviderStatus()
                        : order.getProviderStatus())
                .checkoutUrl(attempt == null ? order.getPaymentLink() : attempt.getCheckoutUrl())
                .paymentLink(order.getPaymentLink())
                .expiresAt(order.getExpiresAt())
                .build();
    }

    private String generateOrderCode(UUID id) {
        return "PAY-" + id.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private long generateProviderOrderCode(UUID id, int attemptNo) {
        while (true) {
            long bucket = Math.max(0L, System.currentTimeMillis() - PROVIDER_ORDER_CODE_EPOCH_MILLIS);
            int sequence = nextProviderOrderCodeSequence(bucket);
            if (sequence < 0) {
                Thread.onSpinWait();
                continue;
            }
            long candidate = bucket * PROVIDER_ORDER_CODE_SEQUENCE_MOD + sequence;
            if (candidate > 0 && candidate <= PAYOS_MAX_SAFE_ORDER_CODE) {
                return candidate;
            }
        }
    }

    private int nextProviderOrderCodeSequence(long bucket) {
        while (true) {
            long lastBucket = PROVIDER_ORDER_CODE_LAST_BUCKET.get();
            if (lastBucket != bucket) {
                if (PROVIDER_ORDER_CODE_LAST_BUCKET.compareAndSet(lastBucket, bucket)) {
                    PROVIDER_ORDER_CODE_SEQUENCE.set(Math.floorMod(attemptNoSeed(bucket), PROVIDER_ORDER_CODE_SEQUENCE_MOD));
                }
                continue;
            }

            int current = PROVIDER_ORDER_CODE_SEQUENCE.getAndIncrement();
            if (current < PROVIDER_ORDER_CODE_SEQUENCE_MOD) {
                return current;
            }

            return -1;
        }
    }

    private int attemptNoSeed(long bucket) {
        long mixed = bucket ^ (bucket >>> 17) ^ (bucket >>> 31);
        return (int) Math.floorMod(mixed, PROVIDER_ORDER_CODE_SEQUENCE_MOD);
    }
}
