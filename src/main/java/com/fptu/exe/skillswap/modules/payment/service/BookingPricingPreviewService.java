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
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only pricing surface. Checkout remains the only path that reserves value. */
@Service
@RequiredArgsConstructor
public class BookingPricingPreviewService implements BookingPricingPreviewPort {

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
    public BookingPricingEstimate estimateForCandidate(UUID viewerUserId, ServiceSlotCandidate candidate) {
        if (viewerUserId == null || candidate == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu để tính giá preview");
        }
        int basePrice = Boolean.TRUE.equals(candidate.isFree()) ? 0 : Math.max(0, candidate.priceScoin() == null ? 0 : candidate.priceScoin());
        int beforeCampaign = PricingPolicy.menteePayableScoin(basePrice, paymentProperties);
        BookingCheckoutSnapshot pricingContext = new BookingCheckoutSnapshot(
                null, null, candidate.mentorUserId(), candidate.serviceId(), candidate.isFree(),
                basePrice, candidate.durationMinutes(), null, null, null);
        CampaignService.CampaignCreditApplication campaign =
                campaignService.estimateCampaignCredit(viewerUserId, pricingContext, beforeCampaign);
        int campaignDiscount = Math.max(0, Math.min(beforeCampaign, campaign.appliedScoin()));
        return new BookingPricingEstimate(
                PRICING_VERSION,
                com.fptu.exe.skillswap.shared.time.BusinessTime.toOffsetDateTime(timeProvider.instant()),
                candidate.serviceId(),
                beforeCampaign,
                beforeCampaign,
                campaignDiscount,
                Math.max(0, beforeCampaign - campaignDiscount),
                campaign.campaignName(),
                true,
                ESTIMATE_DISCLAIMER
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
