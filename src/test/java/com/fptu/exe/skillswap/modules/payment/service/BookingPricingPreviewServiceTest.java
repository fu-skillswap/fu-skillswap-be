package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingEligibilityQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPricingEstimate;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.modules.payment.dto.response.ServicePricingPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.port.ServicePricingQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPricingPreviewServiceTest {

    @Mock
    private MentorBookingQueryPort mentorBookingQueryPort;
    @Mock
    private CampaignService campaignService;
    @Mock
    private CouponService couponService;
    @Mock
    private CreditLedgerService creditLedgerService;
    @Mock
    private BookingEligibilityQueryPort bookingEligibilityQueryPort;

    private PaymentProperties paymentProperties;
    private BookingPricingPreviewService service;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();
        paymentProperties.setMenteeSurchargeBps(1000); // 10%
        service = new BookingPricingPreviewService(
                mentorBookingQueryPort,
                campaignService,
                couponService,
                creditLedgerService,
                paymentProperties,
                bookingEligibilityQueryPort
        );
    }

    @Test
    void previewDiscovery_eligibleMenteeWithWelcome70k_shouldReturnStrikeThroughAndDiscountedPrice() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        ServiceSlotCandidate candidate = new ServiceSlotCandidate(
                serviceId, mentorId, "Review CV", "Desc", "Outcome",
                60, 100_000, false, true, "ONE_TO_ONE", "ONLINE", false);
        MentorBookingCapability capability = new MentorBookingCapability(
                mentorId, "ACTIVE", true, 60, BigDecimal.valueOf(5), 1, null, true, true);
        CouponService.CouponPreviewContext context = CouponService.CouponPreviewContext.empty();
        CouponService.CouponPreviewApplication couponApp = new CouponService.CouponPreviewApplication(
                UUID.randomUUID(), "WELCOME70K", "Ưu đãi chào mừng thành viên mới (Giới hạn 60 suất)", 70_000, 59);

        when(mentorBookingQueryPort.getServiceCandidate(serviceId)).thenReturn(Optional.of(candidate));
        when(mentorBookingQueryPort.getBookingCapability(mentorId)).thenReturn(Optional.of(capability));
        when(bookingEligibilityQueryPort.isDiscoverableMentorForBooking(capability)).thenReturn(true);
        when(couponService.preparePreviewContext(menteeId)).thenReturn(context);
        when(couponService.resolveBestCoupon(context, mentorId, serviceId, 110_000)).thenReturn(couponApp);
        when(campaignService.estimateCampaignCredit(eq(menteeId), any(), eq(40_000)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());

        ServicePricingPreviewResponse response = service.previewDiscovery(menteeId, serviceId);

        assertEquals(110_000, response.priceScoin());
        assertEquals(110_000, response.originalPriceScoin());
        assertEquals(40_000, response.discountedPriceScoin());
        assertEquals(70_000, response.couponDiscountScoin());
        assertTrue(response.hasCouponDiscount());
        assertEquals("WELCOME70K", response.couponCode());
        assertEquals(59, response.couponRemainingQuota());
        assertEquals(40_000, response.estimatedPayableScoin());
    }

    @Test
    void estimateForCandidate_quotaExhausted_shouldReturnFullOriginalPrice() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceSlotCandidate candidate = new ServiceSlotCandidate(
                serviceId, mentorId, "Review CV", "Desc", "Outcome",
                60, 100_000, false, true, "ONE_TO_ONE", "ONLINE", false);
        CouponService.CouponPreviewContext context = CouponService.CouponPreviewContext.empty();

        when(couponService.preparePreviewContext(menteeId)).thenReturn(context);
        when(couponService.resolveBestCoupon(context, mentorId, serviceId, 110_000))
                .thenReturn(CouponService.CouponPreviewApplication.none());
        when(campaignService.estimateCampaignCredit(eq(menteeId), any(), eq(110_000)))
                .thenReturn(CampaignService.CampaignCreditApplication.none());

        BookingPricingEstimate estimate = service.estimateForCandidate(menteeId, candidate);

        assertEquals(110_000, estimate.originalPriceScoin());
        assertEquals(110_000, estimate.discountedPriceScoin());
        assertEquals(0, estimate.couponDiscountScoin());
        assertFalse(estimate.hasCouponDiscount());
        assertNull(estimate.couponCode());
        assertNull(estimate.couponRemainingQuota());
        assertEquals(110_000, estimate.estimatedPayableScoin());
    }

    @Test
    void estimateServicePrices_batchHandlesPaidAndFreeServices() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID paidServiceId = UUID.randomUUID();
        UUID freeServiceId = UUID.randomUUID();
        CouponService.CouponPreviewContext context = CouponService.CouponPreviewContext.empty();
        CouponService.CouponPreviewApplication couponApp = new CouponService.CouponPreviewApplication(
                UUID.randomUUID(), "WELCOME70K", "Welcome 70K", 70_000, 60);

        when(couponService.preparePreviewContext(menteeId)).thenReturn(context);
        when(couponService.resolveBestCoupon(context, mentorId, paidServiceId, 110_000)).thenReturn(couponApp);
        when(campaignService.estimateCampaignCredit(eq(menteeId), any(), anyInt()))
                .thenReturn(CampaignService.CampaignCreditApplication.none());

        Map<UUID, ServicePricingQueryPort.ServicePricePreview> previews = service.estimateServicePrices(
                menteeId,
                List.of(
                        new ServicePricingQueryPort.ServicePriceTarget(paidServiceId, mentorId, false, 100_000),
                        new ServicePricingQueryPort.ServicePriceTarget(freeServiceId, mentorId, true, 0)
                )
        );

        assertEquals(40_000, previews.get(paidServiceId).discountedPriceScoin());
        assertTrue(previews.get(paidServiceId).hasCouponDiscount());
        assertEquals(0, previews.get(freeServiceId).originalPriceScoin());
        assertEquals(0, previews.get(freeServiceId).discountedPriceScoin());
        assertFalse(previews.get(freeServiceId).hasCouponDiscount());
    }
}
