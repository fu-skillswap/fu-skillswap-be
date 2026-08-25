package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingPolicyTest {

    @Test
    void defaultBookingPricing_shouldMatchPublicProductPolicy() {
        PaymentProperties properties = new PaymentProperties();

        assertEquals(110_000, PricingPolicy.menteePayableScoin(100_000, properties));
        assertEquals(95_000, PricingPolicy.mentorNetScoin(100_000, properties));
        assertEquals(15_000, PricingPolicy.menteePayableScoin(100_000, properties)
                - PricingPolicy.mentorNetScoin(100_000, properties));
    }

    @Test
    void serviceLimits_shouldComeFromPaymentConfiguration() {
        PaymentProperties properties = new PaymentProperties();
        properties.setAllowedServiceDurationsMinutes(List.of(45, 75));
        properties.setMinPriceScoinPerMinute(700);
        properties.setMaxPriceScoinPerMinute(10_000);

        assertTrue(PricingPolicy.isAllowedServiceDuration(45, properties));
        assertFalse(PricingPolicy.isAllowedServiceDuration(60, properties));
        assertEquals(31_500, PricingPolicy.minimumPriceForDuration(45, properties));
        assertEquals(450_000, PricingPolicy.maximumPriceForDuration(45, properties));
        assertThrows(BaseException.class, () -> PricingPolicy.validatePaidServicePrice(31_499, 45, properties));
    }
}
