package com.fptu.exe.skillswap.modules.payment.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only service pricing and auto-eligible coupon preview contract exposed by Payment. */
public interface ServicePricingQueryPort {

    ServicePricePreview estimateServicePrice(UUID viewerUserId, ServicePriceTarget target);

    Map<UUID, ServicePricePreview> estimateServicePrices(UUID viewerUserId, List<ServicePriceTarget> targets);

    record ServicePriceTarget(
            UUID serviceId,
            UUID mentorUserId,
            boolean isFree,
            Integer basePriceScoin
    ) {
    }

    record ServicePricePreview(
            UUID serviceId,
            Integer originalPriceScoin,
            Integer discountedPriceScoin,
            Integer couponDiscountScoin,
            Integer campaignDiscountScoin,
            Integer estimatedPayableScoin,
            String couponCode,
            String couponTitle,
            Integer couponRemainingQuota,
            String campaignName
    ) {
        public boolean hasCouponDiscount() {
            return couponDiscountScoin != null && couponDiscountScoin > 0;
        }
    }
}
