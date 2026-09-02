package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutPreviewRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProviderFactory;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProvider;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.fptu.exe.skillswap.shared.time.TimeProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.payment.service.PaymentLifecycleService.isAwaitingPayment;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCheckoutService {

    private final BookingCheckoutQueryPort bookingCheckoutQueryPort;
    private final UserQueryPort userQueryPort;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CouponService couponService;
    private final CreditLedgerService creditLedgerService;
    private final CampaignService campaignService;
    private final PaymentProperties paymentProperties;
    private final PaymentGatewayProviderFactory paymentGatewayProviderFactory;
    private final PaymentWebhookService paymentWebhookService;
    private final PaymentLifecycleService paymentLifecycleService;
    private final PaymentOrderCodeGenerator paymentOrderCodeGenerator;
    private final PaymentResponseMapper paymentResponseMapper;
    private final InternalTelemetryService internalTelemetryService;
    private final TransactionTemplate transactionTemplate;
    private BookingPricingPreviewService bookingPricingPreviewService;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    void setBookingPricingPreviewService(BookingPricingPreviewService bookingPricingPreviewService) {
        this.bookingPricingPreviewService = bookingPricingPreviewService;
    }

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutPreviewResponse previewCheckout(
            UUID currentUserId,
            UUID bookingId,
            PaymentCheckoutPreviewRequest request
    ) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        BookingCheckoutSnapshot booking = bookingCheckoutQueryPort.findCheckoutSnapshot(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        validateCheckoutOwnership(currentUserId, booking);
        if (Boolean.TRUE.equals(booking.serviceIsFree())
                || (booking.servicePriceScoin() != null && booking.servicePriceScoin() == 0)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không cần thanh toán cho dịch vụ miễn phí");
        }
        if (bookingPricingPreviewService == null) {
            throw new IllegalStateException("BookingPricingPreviewService is required for checkout preview");
        }
        return bookingPricingPreviewService.previewCheckout(
                currentUserId,
                booking,
                request == null ? null : request.couponCode(),
                paymentDeadline(booking)
        );
    }

    public PaymentCheckoutResponse checkout(UUID currentUserId, PaymentCheckoutRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || request.bookingId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "bookingId không được để trống");
        }

        CheckoutPreparation preparation = transactionTemplate.execute(status -> {
            BookingCheckoutSnapshot booking = bookingCheckoutQueryPort.findCheckoutSnapshotForUpdate(request.bookingId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
            validateCheckoutOwnership(currentUserId, booking);

            if (Boolean.TRUE.equals(booking.serviceIsFree())
                    || (booking.servicePriceScoin() != null && booking.servicePriceScoin() == 0)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Không cần thanh toán cho dịch vụ miễn phí");
            }

            PaymentOrder existingOrder = paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, booking.bookingId()).orElse(null);
            PaymentAttempt latestAttempt = existingOrder == null
                    ? null
                    : paymentAttemptRepository.findFirstByPaymentOrderIdOrderByAttemptNoDesc(existingOrder.getId()).orElse(null);

            if (existingOrder != null && existingOrder.getStatus() == PaymentOrderStatus.PAID) {
                return CheckoutPreparation.existing(existingOrder, latestAttempt);
            }
            if (existingOrder != null && isAwaitingPayment(existingOrder.getStatus()) && !isExpired(existingOrder)) {
                return CheckoutPreparation.existing(existingOrder, latestAttempt);
            }

            int originalPriceScoin = resolveBasePriceScoin(booking);
            int menteePayablePrice = PricingPolicy.menteePayableScoin(originalPriceScoin, paymentProperties);

            var coupon = couponService == null ? null : couponService.resolveCoupon(request.couponCode());
            if (couponService != null) {
                couponService.validateApplicable(coupon, booking, currentUserId, menteePayablePrice);
            }
            int couponDiscountScoin = couponService == null ? 0 : couponService.calculateCouponDiscount(coupon, menteePayablePrice);
            int amountAfterCoupon = Math.max(0, menteePayablePrice - couponDiscountScoin);

            CampaignService.CampaignCreditApplication campaignApplication = campaignService == null
                    ? CampaignService.CampaignCreditApplication.none()
                    : campaignService.resolveCampaignCredit(currentUserId, booking, amountAfterCoupon);
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
                        : PaymentOrderStatus.PENDING);
            } else {
                draftOrder.setUserCreditScoin(0);
                draftOrder.setRemainingPayableScoin(0);
                draftOrder.setStatus(PaymentOrderStatus.PENDING);
            }
            PaymentOrder savedOrder = paymentOrderRepository.save(draftOrder);
            if (coupon != null && couponService != null) {
                couponService.reserveCoupon(coupon, savedOrder.getId(), currentUserId, savedOrder.getCouponDiscountScoin());
            }

            int nextAttemptNo = (int) paymentAttemptRepository.countByPaymentOrderId(savedOrder.getId()) + 1;
            if (savedOrder.getRemainingPayableScoin() == null || savedOrder.getRemainingPayableScoin() == 0) {
                PaymentAttempt attempt = PaymentAttempt.builder()
                        .paymentOrderId(savedOrder.getId())
                        .attemptNo(nextAttemptNo)
                        .status(PaymentAttemptStatus.SUCCEEDED)
                        .providerStatus("PAID")
                        .build();
                PaymentAttempt persistedAttempt = paymentAttemptRepository.save(attempt);
                if (persistedAttempt != null) {
                    attempt = persistedAttempt;
                }
                paymentWebhookService.finalizeInternalPayment(savedOrder, attempt, null, null, "PAID");
                return CheckoutPreparation.internalPaid(savedOrder, attempt);
            }

            long providerOrderCode = paymentOrderCodeGenerator.generateProviderOrderCode(savedOrder.getId(), nextAttemptNo);
            PaymentAttempt attempt = PaymentAttempt.builder()
                    .paymentOrderId(savedOrder.getId())
                    .attemptNo(nextAttemptNo)
                    .status(PaymentAttemptStatus.CREATING)
                    .providerOrderCode(String.valueOf(providerOrderCode))
                    .build();
            PaymentAttempt persistedAttempt = paymentAttemptRepository.save(attempt);
            if (persistedAttempt != null) {
                attempt = persistedAttempt;
            }
            savedOrder.setProviderOrderCode(String.valueOf(providerOrderCode));
            savedOrder.setProviderStatus("CREATING");
            paymentOrderRepository.save(savedOrder);
            return CheckoutPreparation.providerCreation(savedOrder, attempt, booking);
        });

        if (!preparation.providerCreationRequired()) {
            return paymentResponseMapper.toResponse(preparation.order(), preparation.attempt());
        }

        PaymentGatewayProvider.CreatePaymentLinkResult createResult;
        try {
            createResult = paymentGatewayProviderFactory.getProvider(PaymentProvider.PAYOS)
                    .createPaymentLink(buildCreatePaymentLinkCommand(
                            preparation.booking(), preparation.order(), parseProviderOrderCode(preparation.attempt().getProviderOrderCode())));
        } catch (RuntimeException ex) {
            failProviderAttempt(preparation.order().getId(), preparation.attempt().getId(), ex);
            throw ex;
        }

        return completeProviderAttemptCreation(preparation.order().getId(), preparation.attempt().getId(), createResult, currentUserId);
    }

    private PaymentCheckoutResponse completeProviderAttemptCreation(UUID paymentOrderId,
                                                                    UUID paymentAttemptId,
                                                                    PaymentGatewayProvider.CreatePaymentLinkResult createResult,
                                                                    UUID currentUserId) {
        return transactionTemplate.execute(status -> {
            // This phase does not mutate booking. Keep the shared payment lock order:
            // payment order -> payment attempt.
            PaymentOrder order = paymentOrderRepository.findByIdForUpdate(paymentOrderId).orElseThrow();
            PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(paymentAttemptId).orElseThrow();
            validateAttemptBelongsToOrder(order, attempt);
            if (attempt.getStatus() != PaymentAttemptStatus.CREATING) {
                return paymentResponseMapper.toResponse(order, attempt);
            }
            attempt.setStatus(PaymentAttemptStatus.CREATED);
            attempt.setProviderOrderCode(createResult.providerOrderCode());
            attempt.setProviderPaymentLinkId(createResult.providerPaymentLinkId());
            attempt.setProviderStatus(createResult.providerStatus());
            attempt.setCheckoutUrl(createResult.checkoutUrl());
            order.setProviderOrderCode(createResult.providerOrderCode());
            order.setProviderPaymentLinkId(createResult.providerPaymentLinkId());
            order.setProviderStatus(createResult.providerStatus());
            order.setPaymentLink(createResult.checkoutUrl());
            order.setExpiresAtUtc(createResult.expiresAtUtc());
            order.setExpiresAt(createResult.expiresAt());
            paymentAttemptRepository.save(attempt);
            paymentOrderRepository.save(order);
            if (internalTelemetryService != null) {
                internalTelemetryService.record(
                        "PAYMENT_STARTED", currentUserId, "BOOKING", order.getTargetId(),
                        Map.of("paymentOrderId", String.valueOf(order.getId()))
                );
            }
            return paymentResponseMapper.toResponse(order, attempt);
        });
    }

    private void failProviderAttempt(UUID paymentOrderId, UUID paymentAttemptId, RuntimeException cause) {
        transactionTemplate.executeWithoutResult(status -> {
            PaymentOrder order = paymentOrderRepository.findByIdForUpdate(paymentOrderId).orElseThrow();
            PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(paymentAttemptId).orElseThrow();
            validateAttemptBelongsToOrder(order, attempt);
            if (attempt.getStatus() == PaymentAttemptStatus.CREATING) {
                paymentWebhookService.markAttemptFinalState(attempt, PaymentAttemptStatus.FAILED, null, null, "CREATE_FAILED", cause.getMessage());
                paymentLifecycleService.cancelAwaitingPaymentOrder(order);
            }
        });
    }

    private void validateAttemptBelongsToOrder(PaymentOrder order, PaymentAttempt attempt) {
        if (order == null || attempt == null || order.getId() == null
                || !order.getId().equals(attempt.getPaymentOrderId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Payment attempt không thuộc payment order cần xử lý");
        }
    }

    private void validateCheckoutOwnership(UUID currentUserId, BookingCheckoutSnapshot booking) {
        if (booking.menteeUserId() == null || !currentUserId.equals(booking.menteeUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentee của booking mới có thể thanh toán");
        }
        if (booking.mentorUserId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking không gắn với mentor hợp lệ");
        }
        if ("PAID".equals(booking.status())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking này đã được thanh toán trước đó");
        }
        if ("CANCELLED_BY_MENTEE".equals(booking.status())
                || "CANCELLED_BY_MENTOR".equals(booking.status())
                || "REJECTED".equals(booking.status())
                || "EXPIRED".equals(booking.status())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Booking đã kết thúc ở trạng thái " + booking.status() + " và không thể thanh toán");
        }
        if (!"ACCEPTED_AWAITING_PAYMENT".equals(booking.status())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa sẵn sàng để thanh toán (trạng thái: " + booking.status() + ")");
        }
        Instant deadlineUtc = paymentDeadlineUtc(booking);
        if (deadlineUtc != null && !deadlineUtc.isAfter(timeProvider.instant())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking đã quá hạn thanh toán");
        }
    }

    private Instant paymentDeadlineUtc(BookingCheckoutSnapshot booking) {
        return booking.paymentDeadlineUtc();
    }

    private OffsetDateTime paymentDeadline(BookingCheckoutSnapshot booking) {
        Instant deadlineUtc = paymentDeadlineUtc(booking);
        return deadlineUtc != null
                ? com.fptu.exe.skillswap.shared.time.BusinessTime.toOffsetDateTime(deadlineUtc)
                : null;
    }

    private int resolveBasePriceScoin(BookingCheckoutSnapshot booking) {
        boolean isFree = Boolean.TRUE.equals(booking.serviceIsFree());
        int basePriceScoin = booking.servicePriceScoin() != null
                ? booking.servicePriceScoin()
                : 0;
        if (isFree) {
            return 0;
        }
        Integer durationMinutes = booking.serviceDurationMinutes();
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ mentoring đang có thời lượng không hợp lệ");
        }
        int normalizedPrice = Math.max(0, basePriceScoin);
        PricingPolicy.validatePaidServicePrice(normalizedPrice, durationMinutes, paymentProperties);
        return normalizedPrice;
    }

    private void prepareOrderForCheckout(PaymentOrder draftOrder,
                                         BookingCheckoutSnapshot booking,
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
            draftOrder.setOrderCode(paymentOrderCodeGenerator.generateOrderCode(paymentOrderId));
        }
        draftOrder.setTargetType(PaymentTargetType.BOOKING);
        draftOrder.setTargetId(booking.bookingId());
        draftOrder.setPayerUserId(currentUserId);
        draftOrder.setMentorUserId(booking.mentorUserId());
        draftOrder.setServiceId(booking.serviceId());

        int menteeSurchargeBps = paymentProperties.getMenteeSurchargeBps();
        int mentorCommissionBps = paymentProperties.getMentorCommissionBps();

        int mentorNetScoin = PricingPolicy.mentorNetScoin(originalPriceScoin, paymentProperties);

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
        if (creditLedgerService == null) {
            return 0;
        }
        var balances = creditLedgerService.getAvailableBalanceByOrigin(currentUserId);
        int availableCredit = balances.entrySet().stream()
                .filter(e -> List.of(CreditOriginType.CAMPAIGN_BONUS, CreditOriginType.COUPON_BONUS, CreditOriginType.REFUND, CreditOriginType.MANUAL).contains(e.getKey()))
                .mapToInt(Map.Entry::getValue)
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

    private PaymentGatewayProvider.CreatePaymentLinkCommand buildCreatePaymentLinkCommand(BookingCheckoutSnapshot booking,
                                                                                PaymentOrder order,
                                                                                long providerOrderCode) {
        long expiredAtEpoch = resolveProviderLinkExpiryUtc(booking).getEpochSecond();
        UserSummaryRecord mentee = userQueryPort.findUserSummaryById(booking.menteeUserId()).orElse(null);
        return new PaymentGatewayProvider.CreatePaymentLinkCommand(
                providerOrderCode,
                PricingPolicy.toVnd(order.getRemainingPayableScoin(), paymentProperties),
                buildProviderDescription(order),
                paymentProperties.getPayos().getReturnUrl(),
                paymentProperties.getPayos().getCancelUrl(),
                expiredAtEpoch,
                mentee == null ? null : mentee.fullName(),
                mentee == null ? null : mentee.email(),
                null,
                List.of(new PaymentGatewayProvider.PaymentItem(
                        buildPaymentItemName(booking),
                        1,
                        PricingPolicy.toVnd(order.getRemainingPayableScoin(), paymentProperties)
                ))
        );
    }

    private String buildProviderDescription(PaymentOrder order) {
        String seed = order.getOrderCode() == null ? "SkillSwap" : order.getOrderCode().replaceAll("[^A-Za-z0-9]", "");
        String description = "SkillSwap" + seed;
        return description.length() > 25 ? description.substring(0, 25) : description;
    }

    private String buildPaymentItemName(BookingCheckoutSnapshot booking) {
        if (StringUtils.hasText(booking.serviceTitle())) {
            return booking.serviceTitle();
        }
        return "SkillSwap mentoring session";
    }

    private boolean hasInternalCoverage(int basePriceScoin, int couponDiscountScoin, int campaignCreditAppliedScoin, int userCreditAppliedScoin) {
        return couponDiscountScoin > 0
                || campaignCreditAppliedScoin > 0
                || userCreditAppliedScoin > 0
                || basePriceScoin == 0;
    }

    private boolean isExpired(PaymentOrder order) {
        if (order.getExpiresAtUtc() != null) {
            return !order.getExpiresAtUtc().isAfter(timeProvider.instant());
        }
        return order.getExpiresAt() != null && !order.getExpiresAt().isAfter(timeProvider.nowBusiness());
    }

    /**
     * The gateway link must never outlive the booking's server-enforced payment window.
     * This protects the short-window case where a session is close to starting.
     */
    Instant resolveProviderLinkExpiryUtc(BookingCheckoutSnapshot booking) {
        Instant nowUtc = timeProvider.instant();
        Instant configuredLinkExpiryUtc = nowUtc.plus(Duration.ofMinutes(paymentProperties.getPaymentLinkExpiryMinutes()));
        Instant bookingDeadlineUtc = paymentDeadlineUtc(booking);
        Instant effectiveExpiryUtc = bookingDeadlineUtc != null && bookingDeadlineUtc.isBefore(configuredLinkExpiryUtc)
                ? bookingDeadlineUtc
                : configuredLinkExpiryUtc;
        if (!effectiveExpiryUtc.isAfter(nowUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking đã quá hạn thanh toán");
        }
        return effectiveExpiryUtc;
    }

    private long parseProviderOrderCode(String providerOrderCode) {
        try {
            return Long.parseLong(providerOrderCode);
        } catch (NumberFormatException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "providerOrderCode PayOS hiện tại không hợp lệ");
        }
    }

    private record CheckoutPreparation(
            PaymentOrder order,
            PaymentAttempt attempt,
            BookingCheckoutSnapshot booking,
            boolean providerCreationRequired
    ) {
        private static CheckoutPreparation existing(PaymentOrder order, PaymentAttempt attempt) {
            return new CheckoutPreparation(order, attempt, null, false);
        }

        private static CheckoutPreparation internalPaid(PaymentOrder order, PaymentAttempt attempt) {
            return new CheckoutPreparation(order, attempt, null, false);
        }

        private static CheckoutPreparation providerCreation(PaymentOrder order, PaymentAttempt attempt, BookingCheckoutSnapshot booking) {
            return new CheckoutPreparation(order, attempt, booking, true);
        }
    }
}
