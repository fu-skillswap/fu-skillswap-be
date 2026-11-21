package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingEligibilityQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPricingEstimate;
import com.fptu.exe.skillswap.modules.booking.port.BookingPricingPreviewPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.ServicePricingPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.port.ServicePricingQueryPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only pricing surface. Checkout remains the only path that reserves value. */
@Service
@RequiredArgsConstructor
public class BookingPricingPreviewService implements BookingPricingPreviewPort, ServicePricingQueryPort {

    public static final String PRICING_VERSION = "v1";
    public static final String ESTIMATE_DISCLAIMER = BookingPricingPreviewPort.ESTIMATE_DISCLAIMER;
    private static final List<CreditOriginType> USABLE_CREDIT_ORIGINS = List.of(
            CreditOriginType.CAMPAIGN_BONUS,
            CreditOriginType.COUPON_BONUS,
            CreditOriginType.REFUND,
            CreditOriginType.MANUAL
    );

    private final MentorBookingQueryPort mentorBookingQueryPort;
    private final CampaignService campaignService;
    private final CouponService couponService;
    private final CreditLedgerService creditLedgerService;
    private final PaymentProperties paymentProperties;
    private final BookingEligibilityQueryPort bookingEligibilityQueryPort;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional(readOnly = true)
    public ServicePricingPreviewResponse previewDiscovery(UUID viewerUserId, UUID serviceId) {
        if (viewerUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        ServiceSlotCandidate candidate = mentorBookingQueryPort.getServiceCandidate(serviceId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dịch vụ mentoring"));
        validateDiscoverable(candidate);
        BookingPricingEstimate estimate = estimateForCandidate(viewerUserId, candidate);
        return new ServicePricingPreviewResponse(
                estimate.pricingVersion(),
                estimate.calculatedAt(),
                estimate.serviceId(),
                estimate.priceScoin(),
                estimate.originalPriceScoin(),
                estimate.discountedPriceScoin(),
                estimate.couponDiscountScoin(),
                estimate.hasCouponDiscount(),
                estimate.couponCode(),
                estimate.couponTitle(),
                estimate.couponRemainingQuota(),
                estimate.priceBeforeCampaignScoin(),
                estimate.campaignDiscountScoin(),
                estimate.estimatedPayableScoin(),
                estimate.campaignName(),
                estimate.isEstimate(),
                estimate.disclaimer()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ServicePricePreview estimateServicePrice(UUID viewerUserId, ServicePriceTarget target) {
        if (target == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu dịch vụ để tính giá");
        }
        CouponService.CouponPreviewContext couponContext = couponService == null
                ? CouponService.CouponPreviewContext.empty()
                : couponService.preparePreviewContext(viewerUserId);
        return buildServicePricePreview(couponContext, viewerUserId, target, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ServicePricePreview> estimateServicePrices(UUID viewerUserId, List<ServicePriceTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return Map.of();
        }
        CouponService.CouponPreviewContext couponContext = couponService == null
                ? CouponService.CouponPreviewContext.empty()
                : couponService.preparePreviewContext(viewerUserId);
        Map<UUID, ServicePricePreview> results = new LinkedHashMap<>();
        for (ServicePriceTarget target : targets) {
            if (target == null || target.serviceId() == null || results.containsKey(target.serviceId())) {
                continue;
            }
            results.put(target.serviceId(), buildServicePricePreview(couponContext, viewerUserId, target, null));
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingPricingEstimate estimateForCandidate(UUID viewerUserId, ServiceSlotCandidate candidate) {
        if (viewerUserId == null || candidate == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu để tính giá preview");
        }
        CouponService.CouponPreviewContext couponContext = couponService == null
                ? CouponService.CouponPreviewContext.empty()
                : couponService.preparePreviewContext(viewerUserId);
        ServicePricePreview preview = buildServicePricePreview(
                couponContext,
                viewerUserId,
                new ServicePriceTarget(
                        candidate.serviceId(),
                        candidate.mentorUserId(),
                        Boolean.TRUE.equals(candidate.isFree()),
                        candidate.priceScoin()
                ),
                candidate.durationMinutes()
        );
        return new BookingPricingEstimate(
                PRICING_VERSION,
                com.fptu.exe.skillswap.shared.time.BusinessTime.toOffsetDateTime(timeProvider.instant()),
                candidate.serviceId(),
                preview.originalPriceScoin(),
                preview.originalPriceScoin(),
                preview.discountedPriceScoin(),
                preview.couponDiscountScoin(),
                preview.hasCouponDiscount(),
                preview.couponCode(),
                preview.couponTitle(),
                preview.couponRemainingQuota(),
                preview.discountedPriceScoin(),
                preview.campaignDiscountScoin(),
                preview.estimatedPayableScoin(),
                preview.campaignName(),
                true,
                ESTIMATE_DISCLAIMER
        );
    }

    private ServicePricePreview buildServicePricePreview(
            CouponService.CouponPreviewContext couponContext,
            UUID viewerUserId,
            ServicePriceTarget target,
            Integer durationMinutes
    ) {
        int basePrice = target.isFree() ? 0 : Math.max(0, target.basePriceScoin() == null ? 0 : target.basePriceScoin());
        int originalPrice = basePrice == 0 ? 0 : PricingPolicy.menteePayableScoin(basePrice, paymentProperties);

        CouponService.CouponPreviewApplication coupon = (couponService == null || originalPrice <= 0)
                ? CouponService.CouponPreviewApplication.none()
                : couponService.resolveBestCoupon(couponContext, target.mentorUserId(), target.serviceId(), originalPrice);
        int couponDiscount = coupon.applied() ? Math.max(0, Math.min(originalPrice, coupon.discountScoin())) : 0;
        int afterCoupon = Math.max(0, originalPrice - couponDiscount);

        CampaignService.CampaignCreditApplication campaign = CampaignService.CampaignCreditApplication.none();
        if (viewerUserId != null && afterCoupon > 0 && campaignService != null) {
            BookingCheckoutSnapshot pricingContext = new BookingCheckoutSnapshot(
                    null, null, target.mentorUserId(), target.serviceId(), target.isFree(),
                    basePrice, durationMinutes, null, null, null);
            campaign = campaignService.estimateCampaignCredit(viewerUserId, pricingContext, afterCoupon);
        }
        int campaignDiscount = Math.max(0, Math.min(afterCoupon, campaign.appliedScoin()));
        int estimatedPayable = Math.max(0, afterCoupon - campaignDiscount);

        return new ServicePricePreview(
                target.serviceId(),
                originalPrice,
                afterCoupon,
                couponDiscount,
                campaignDiscount,
                estimatedPayable,
                coupon.applied() ? coupon.couponCode() : null,
                coupon.applied() ? coupon.couponTitle() : null,
                coupon.applied() ? coupon.remainingQuota() : null,
                campaign.campaignName()
        );
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutPreviewResponse previewCheckout(
            UUID viewerUserId,
            BookingCheckoutSnapshot booking,
            String couponCode,
            OffsetDateTime paymentDeadlineAt
    ) {
        if (booking == null || booking.serviceId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking không có service hợp lệ để tính giá");
        }
        int basePrice = Boolean.TRUE.equals(booking.serviceIsFree()) ? 0
                : Math.max(0, booking.servicePriceScoin() == null ? 0 : booking.servicePriceScoin());
        int beforeDiscount = PricingPolicy.menteePayableScoin(basePrice, paymentProperties);

        Coupon coupon = couponService.resolveCouponForPreview(couponCode);
        couponService.validateApplicable(coupon, booking, viewerUserId, beforeDiscount);
        int couponDiscount = couponService.calculateCouponDiscount(coupon, beforeDiscount);
        int afterCoupon = Math.max(0, beforeDiscount - couponDiscount);
        CampaignService.CampaignCreditApplication campaign =
                campaignService.estimateCampaignCredit(viewerUserId, booking, afterCoupon);
        int campaignCredit = Math.max(0, Math.min(afterCoupon, campaign.appliedScoin()));
        int afterCampaign = Math.max(0, afterCoupon - campaignCredit);
        int availableCredit = availableUserCredit(viewerUserId);
        int userCredit = Math.min(afterCampaign, availableCredit);

        return new PaymentCheckoutPreviewResponse(
                booking.bookingId(),
                beforeDiscount,
                beforeDiscount,
                couponDiscount,
                campaignCredit,
                userCredit,
                Math.max(0, afterCampaign - userCredit),
                paymentDeadlineAt,
                true,
                ESTIMATE_DISCLAIMER
        );
    }

    private int availableUserCredit(UUID viewerUserId) {
        return creditLedgerService.getAvailableBalanceByOriginForPreview(viewerUserId).entrySet().stream()
                .filter(entry -> USABLE_CREDIT_ORIGINS.contains(entry.getKey()))
                .mapToInt(entry -> entry.getValue() == null ? 0 : entry.getValue())
                .sum();
    }

    private void validateDiscoverable(ServiceSlotCandidate candidate) {
        if (candidate == null || !Boolean.TRUE.equals(candidate.active()) || candidate.mentorUserId() == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dịch vụ mentoring khả dụng");
        }
        UUID mentorUserId = candidate.mentorUserId();
        MentorBookingCapability capability = mentorBookingQueryPort.getBookingCapability(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dịch vụ mentoring khả dụng"));
        if (!bookingEligibilityQueryPort.isDiscoverableMentorForBooking(capability)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dịch vụ mentoring khả dụng");
        }
    }
}
