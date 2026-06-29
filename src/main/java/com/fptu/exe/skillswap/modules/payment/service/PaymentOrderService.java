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
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

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
        validateCheckoutOwnership(currentUserId, booking);

        PaymentOrder existingOrder = paymentOrderRepository.findByBookingId(booking.getId()).orElse(null);
        PaymentAttempt latestAttempt = existingOrder == null
                ? null
                : paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(existingOrder.getId()).orElse(null);
        if (existingOrder != null && latestAttempt != null) {
            trySynchronizeProviderStatus(existingOrder, latestAttempt);
        }

        if (existingOrder != null && existingOrder.getStatus() == PaymentOrderStatus.PAID) {
            return toResponse(existingOrder, latestAttempt);
        }
        if (existingOrder != null && isAwaitingPayment(existingOrder.getStatus()) && !isExpired(existingOrder)) {
            return toResponse(existingOrder, latestAttempt);
        }

        int basePriceScoin = resolveBasePriceScoin(booking);
        int commissionRateBps = paymentProperties.getPlatformCommissionBps();

        var coupon = couponService.resolveCoupon(request.couponCode());
        couponService.validateApplicable(coupon, booking, currentUserId, basePriceScoin);
        int couponDiscountScoin = couponService.calculateCouponDiscount(coupon, basePriceScoin);
        int amountAfterCoupon = Math.max(0, basePriceScoin - couponDiscountScoin);

        CampaignService.CampaignCreditApplication campaignApplication =
                campaignService.resolveCampaignCredit(currentUserId, booking, amountAfterCoupon);
        int campaignCreditAppliedScoin = Math.max(0, Math.min(amountAfterCoupon, campaignApplication.appliedScoin()));
        int amountAfterCampaign = Math.max(0, amountAfterCoupon - campaignCreditAppliedScoin);

        PaymentOrder draftOrder = existingOrder != null ? existingOrder : new PaymentOrder();
        prepareOrderForCheckout(draftOrder, booking, currentUserId, coupon, campaignApplication,
                basePriceScoin, couponDiscountScoin, campaignCreditAppliedScoin, commissionRateBps);

        if (amountAfterCampaign > 0) {
            int userCredit = reserveUserCredit(currentUserId, draftOrder, amountAfterCampaign);
            draftOrder.setUserCreditScoin(userCredit);
            draftOrder.setRemainingPayableScoin(Math.max(0, amountAfterCampaign - userCredit));
            draftOrder.setStatus(draftOrder.getRemainingPayableScoin() > 0
                    ? hasInternalCoverage(basePriceScoin, couponDiscountScoin, campaignCreditAppliedScoin, userCredit)
                        ? PaymentOrderStatus.PARTIALLY_COVERED_BY_CREDIT
                        : PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT
                    : PaymentOrderStatus.PAID);
        } else {
            draftOrder.setUserCreditScoin(0);
            draftOrder.setRemainingPayableScoin(0);
            draftOrder.setStatus(PaymentOrderStatus.PAID);
        }

        PaymentOrder savedOrder = paymentOrderRepository.save(draftOrder);
        int nextAttemptNo = (int) paymentAttemptRepository.countByPaymentOrderId(savedOrder.getId()) + 1;

        PaymentAttempt attempt;
        if (savedOrder.getRemainingPayableScoin() > 0) {
            long providerOrderCode = generateProviderOrderCode(savedOrder.getId(), nextAttemptNo);
            PayOsGateway.CreatePaymentLinkResult createResult = payOsGateway.createPaymentLink(
                    buildCreatePaymentLinkCommand(booking, savedOrder, providerOrderCode)
            );
            savedOrder.setProviderOrderCode(createResult.providerOrderCode());
            savedOrder.setProviderPaymentLinkId(createResult.providerPaymentLinkId());
            savedOrder.setProviderStatus(createResult.providerStatus());
            savedOrder.setPaymentLink(createResult.checkoutUrl());
            savedOrder.setExpiresAt(createResult.expiresAt());
            savedOrder = paymentOrderRepository.save(savedOrder);

            attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                    .paymentOrderId(savedOrder.getId())
                    .attemptNo(nextAttemptNo)
                    .status(PaymentAttemptStatus.REDIRECTED)
                    .providerOrderCode(createResult.providerOrderCode())
                    .providerPaymentLinkId(createResult.providerPaymentLinkId())
                    .providerStatus(createResult.providerStatus())
                    .checkoutUrl(createResult.checkoutUrl())
                    .build());
        } else {
            savedOrder.setProviderOrderCode(null);
            savedOrder.setProviderPaymentLinkId(null);
            savedOrder.setProviderStatus("PAID");
            savedOrder.setPaymentLink(null);
            savedOrder.setExpiresAt(null);
            savedOrder = paymentOrderRepository.save(savedOrder);
            attempt = paymentAttemptRepository.save(PaymentAttempt.builder()
                    .paymentOrderId(savedOrder.getId())
                    .attemptNo(nextAttemptNo)
                    .status(PaymentAttemptStatus.SUCCEEDED)
                    .providerStatus("PAID")
                    .build());
        }

        if (coupon != null) {
            couponService.reserveCoupon(coupon, savedOrder.getId(), currentUserId, couponDiscountScoin);
        }
        if (savedOrder.getStatus() == PaymentOrderStatus.PAID) {
            finalizeInternalPayment(savedOrder, attempt, null, null, "PAID");
            savedOrder = paymentOrderRepository.save(savedOrder);
        }
        return toResponse(savedOrder, attempt);
    }

    @Transactional
    public PaymentCheckoutResponse handleWebhook(PaymentWebhookRequest request) {
        if (request == null || request.data() == null || request.data().orderCode() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Webhook PayOS thiếu data.orderCode");
        }

        PayOsGateway.VerifiedWebhook verified = payOsGateway.verifyWebhook(request);
        if (!verified.success() || !isPaidProviderWebhook(verified.providerStatus())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Webhook PayOS chưa xác nhận thanh toán thành công");
        }

        PaymentAttempt attempt = paymentAttemptRepository.findByProviderOrderCodeForUpdate(verified.providerOrderCode())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy payment attempt tương ứng với orderCode PayOS"));
        PaymentOrder order = paymentOrderRepository.findByIdForUpdate(attempt.getPaymentOrderId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));

        String providerEventId = resolveProviderEventId(verified);
        if (StringUtils.hasText(providerEventId)
                && (paymentOrderRepository.existsByProviderEventId(providerEventId)
                || paymentAttemptRepository.existsByProviderEventId(providerEventId))) {
            return toResponse(order, attempt);
        }
        if (isFinal(order.getStatus())) {
            return toResponse(order, attempt);
        }

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
        finalizeInternalPayment(order, attempt, verified.providerTransactionId(), providerEventId, "PAID");
        order = paymentOrderRepository.save(order);
        return toResponse(order, attempt);
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
    public void handleMenteeCancellation(Booking booking, boolean lateCancellation) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByBookingId(booking.getId()).orElse(null);
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
        PaymentOrder order = paymentOrderRepository.findByBookingId(booking.getId()).orElse(null);
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
        if (booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa sẵn sàng để thanh toán");
        }
    }

    private int resolveBasePriceScoin(Booking booking) {
        int basePriceScoin = booking.getServicePriceScoinSnapshot() != null
                ? booking.getServicePriceScoinSnapshot()
                : (booking.getService() != null && booking.getService().getPriceScoin() != null
                ? booking.getService().getPriceScoin()
                : 0);
        return Math.max(0, basePriceScoin);
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

    @Transactional
    public void expireAwaitingPayment(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        PaymentOrder order = paymentOrderRepository.findByBookingId(booking.getId()).orElse(null);
        if (order == null) {
            return;
        }
        if (isAwaitingPayment(order.getStatus())) {
            cancelAwaitingPaymentOrder(order);
        }
    }

    private void prepareOrderForCheckout(PaymentOrder draftOrder,
                                         Booking booking,
                                         UUID currentUserId,
                                         Coupon coupon,
                                         CampaignService.CampaignCreditApplication campaignApplication,
                                         int basePriceScoin,
                                         int couponDiscountScoin,
                                         int campaignCreditAppliedScoin,
                                         int commissionRateBps) {
        UUID paymentOrderId = draftOrder.getId() != null ? draftOrder.getId() : UuidUtil.generateUuidV7();
        draftOrder.setId(paymentOrderId);
        if (!StringUtils.hasText(draftOrder.getOrderCode())) {
            draftOrder.setOrderCode(generateOrderCode(paymentOrderId));
        }
        draftOrder.setBookingId(booking.getId());
        draftOrder.setPayerUserId(currentUserId);
        draftOrder.setMentorUserId(booking.getMentorProfile().getUserId());
        draftOrder.setServiceId(booking.getService() == null ? null : booking.getService().getId());
        draftOrder.setGrossScoin(basePriceScoin);
        draftOrder.setCommissionRateBps(commissionRateBps);
        draftOrder.setCommissionScoin(Math.max(0, (basePriceScoin * commissionRateBps) / 10_000));
        draftOrder.setMentorNetScoin(Math.max(0, basePriceScoin - draftOrder.getCommissionScoin()));
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
        var reservedEntries = creditLedgerService.reserveCredit(
                currentUserId,
                amountAfterCampaign,
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
        } catch (BaseException ex) {
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

    private void finalizeInternalPayment(PaymentOrder order,
                                         PaymentAttempt attempt,
                                         String providerTransactionId,
                                         String providerEventId,
                                         String providerStatus) {
        if (order.getCreditFinalizedAt() == null) {
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
        order.setProviderTransactionId(providerTransactionId);
        order.setProviderEventId(providerEventId);
        order.setProviderStatus(providerStatus);
        order.setPaidAt(order.getPaidAt() == null ? DateTimeUtil.now() : order.getPaidAt());
        markAttemptFinalState(attempt, PaymentAttemptStatus.SUCCEEDED, providerTransactionId, providerEventId, providerStatus, null);
        finalizePaidBooking(order);
    }

    private void finalizePaidBooking(PaymentOrder order) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(order.getBookingId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để hoàn tất thanh toán"));
        if (booking.getStatus() == BookingStatus.PAID) {
            return;
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "Booking hiện không còn ở trạng thái chờ thanh toán để xác nhận"
            );
        }
        booking.setStatus(BookingStatus.PAID);
        bookingRepository.save(booking);

        sessionService.createForAcceptedBooking(booking);
        conversationService.createDirectForAcceptedBooking(booking);

        notificationService.createNotification(
                booking.getMentorProfile().getUserId(),
                NotificationType.BOOKING_PAYMENT_CONFIRMED,
                "Mentee đã hoàn tất thanh toán và lịch đã được xác nhận",
                booking.getMentee().getFullName() + " đã hoàn tất thanh toán cho lịch mentoring với bạn.",
                "BOOKING",
                booking.getId()
        );

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
        long base = id.getMostSignificantBits() & Long.MAX_VALUE;
        long candidate = base + attemptNo;
        if (candidate <= 0) {
            candidate = Math.abs(id.getLeastSignificantBits()) + attemptNo;
        }
        return candidate;
    }
}
