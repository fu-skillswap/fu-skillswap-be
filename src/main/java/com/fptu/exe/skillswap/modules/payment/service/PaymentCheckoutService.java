package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.booking.service.BookingDeadlinePolicy;
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
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
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

    private final BookingQueryPort bookingQueryPort;
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
        Booking booking = bookingQueryPort.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        validateCheckoutOwnership(currentUserId, booking);
        if (Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot())
                || (booking.getServicePriceScoinSnapshot() != null && booking.getServicePriceScoinSnapshot() == 0)) {
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
            Booking booking = bookingQueryPort.findByIdForSessionUpdate(request.bookingId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
            validateCheckoutOwnership(currentUserId, booking);

            if (Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot())
                    || (booking.getServicePriceScoinSnapshot() != null && booking.getServicePriceScoinSnapshot() == 0)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Không cần thanh toán cho dịch vụ miễn phí");
            }

            PaymentOrder existingOrder = paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
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
                paymentWebhookService.finalizeInternalPayment(savedOrder, attempt, null, null, "PAID", booking);
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
        if (booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa sẵn sàng để thanh toán (trạng thái: " + booking.getStatus() + ")");
        }
        Instant deadlineUtc = paymentDeadlineUtc(booking);
        if (deadlineUtc != null && !deadlineUtc.isAfter(timeProvider.instant())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking đã quá hạn thanh toán");
        }
    }

    private Instant paymentDeadlineUtc(Booking booking) {
        return BookingDeadlinePolicy.resolvePaymentDeadlineUtc(booking);
    }

    private OffsetDateTime paymentDeadline(Booking booking) {
        Instant deadlineUtc = paymentDeadlineUtc(booking);
        return deadlineUtc != null
                ? com.fptu.exe.skillswap.modules.booking.service.BookingTime.toOffsetDateTime(deadlineUtc)
                : null;
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
        PricingPolicy.validatePaidServicePrice(normalizedPrice, durationMinutes);
        return normalizedPrice;
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
            draftOrder.setOrderCode(paymentOrderCodeGenerator.generateOrderCode(paymentOrderId));
        }
        draftOrder.setTargetType(PaymentTargetType.BOOKING);
        draftOrder.setTargetId(booking.getId());
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

    private PaymentGatewayProvider.CreatePaymentLinkCommand buildCreatePaymentLinkCommand(Booking booking,
                                                                                PaymentOrder order,
                                                                                long providerOrderCode) {
        long expiredAtEpoch = timeProvider.instant()
                .plus(Duration.ofMinutes(paymentProperties.getPaymentLinkExpiryMinutes()))
                .getEpochSecond();
        return new PaymentGatewayProvider.CreatePaymentLinkCommand(
                providerOrderCode,
                PricingPolicy.toVnd(order.getRemainingPayableScoin(), paymentProperties),
                buildProviderDescription(order),
                paymentProperties.getPayos().getReturnUrl(),
                paymentProperties.getPayos().getCancelUrl(),
                expiredAtEpoch,
                booking.getMentee() == null ? null : booking.getMentee().getFullName(),
                booking.getMentee() == null ? null : booking.getMentee().getEmail(),
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

    private String buildPaymentItemName(Booking booking) {
        if (StringUtils.hasText(booking.getServiceTitleSnapshot())) {
            return booking.getServiceTitleSnapshot();
        }
        if (booking.getService() != null && StringUtils.hasText(booking.getService().getTitle())) {
            return booking.getService().getTitle();
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
            return order.getExpiresAtUtc().isBefore(timeProvider.instant());
        }
        return order.getExpiresAt() != null && order.getExpiresAt().isBefore(timeProvider.nowBusiness());
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
            Booking booking,
            boolean providerCreationRequired
    ) {
        private static CheckoutPreparation existing(PaymentOrder order, PaymentAttempt attempt) {
            return new CheckoutPreparation(order, attempt, null, false);
        }

        private static CheckoutPreparation internalPaid(PaymentOrder order, PaymentAttempt attempt) {
            return new CheckoutPreparation(order, attempt, null, false);
        }

        private static CheckoutPreparation providerCreation(PaymentOrder order, PaymentAttempt attempt, Booking booking) {
            return new CheckoutPreparation(order, attempt, booking, true);
        }
    }
}
