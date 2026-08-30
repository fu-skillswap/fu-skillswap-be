package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.shared.policy.PricingPolicy;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.ServicePricingPreviewResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptu.exe.skillswap.shared.time.BookingTime;
import java.time.Instant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only pricing surface. Checkout remains the only path that reserves value. */
@Service
@RequiredArgsConstructor
public class BookingPricingPreviewService {

    public static final String PRICING_VERSION = "v1";
    public static final String ESTIMATE_DISCLAIMER = "Final price is calculated at checkout.";
    private static final List<CreditOriginType> USABLE_CREDIT_ORIGINS = List.of(
            CreditOriginType.CAMPAIGN_BONUS,
            CreditOriginType.COUPON_BONUS,
            CreditOriginType.REFUND,
            CreditOriginType.MANUAL
    );

    private final MentorQueryPort mentorQueryPort;
    private final CampaignService campaignService;
    private final CouponService couponService;
    private final CreditLedgerService creditLedgerService;
    private final PaymentProperties paymentProperties;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
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
        MentorService service = mentorQueryPort.findByIdForPricingPreview(serviceId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dịch vụ mentoring"));
        validateDiscoverable(service);
        return estimateForService(viewerUserId, service);
    }

    @Transactional(readOnly = true)
    public ServicePricingPreviewResponse estimateForService(UUID viewerUserId, MentorService service) {
        if (viewerUserId == null || service == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu để tính giá preview");
        }
        int basePrice = normalizedBasePrice(service);
        int beforeCampaign = PricingPolicy.menteePayableScoin(basePrice, paymentProperties);
        Booking pricingContext = Booking.builder()
                .service(service)
                .mentorProfile(service.getMentorProfile())
                .serviceIsFreeSnapshot(service.isFree())
                .servicePriceScoinSnapshot(basePrice)
                .serviceDurationSnapshot(service.getDurationMinutes())
                .build();
        CampaignService.CampaignCreditApplication campaign =
                campaignService.estimateCampaignCredit(viewerUserId, pricingContext, beforeCampaign);
        int campaignDiscount = Math.max(0, Math.min(beforeCampaign, campaign.appliedScoin()));
        return new ServicePricingPreviewResponse(
                PRICING_VERSION,
                BookingTime.toOffsetDateTime(timeProvider.instant()),
                service.getId(),
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
            Booking booking,
            String couponCode,
            OffsetDateTime paymentDeadlineAt
    ) {
        if (booking == null || booking.getService() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking không có service hợp lệ để tính giá");
        }
        int basePrice = Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot()) ? 0
                : Math.max(0, booking.getServicePriceScoinSnapshot() == null ? 0 : booking.getServicePriceScoinSnapshot());
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
                booking.getId(),
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

    private int normalizedBasePrice(MentorService service) {
        if (service.isFree()) {
            return 0;
        }
        return Math.max(0, service.getPriceScoin() == null ? 0 : service.getPriceScoin());
    }

    private void validateDiscoverable(MentorService service) {
        MentorProfile mentor = service.getMentorProfile();
        if (!service.isActive() || mentor == null || mentor.getUser() == null
                || mentor.getStatus() != com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.ACTIVE
                || mentor.getVerifiedAt() == null || !mentor.isAvailable()) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dịch vụ mentoring khả dụng");
        }
    }
}
