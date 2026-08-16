package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.strategy.DiscountStrategyRegistry;
import com.fptu.exe.skillswap.modules.payment.strategy.FixedAmountDiscountStrategy;
import com.fptu.exe.skillswap.modules.payment.strategy.PercentageDiscountStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscountStrategyRegistryTest {

    private DiscountStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DiscountStrategyRegistry(List.of(
                new PercentageDiscountStrategy(),
                new FixedAmountDiscountStrategy()
        ));
    }

    @Test
    void calculateDiscount_percentageWithoutCap() {
        Coupon coupon = Coupon.builder()
                .discountType(CouponDiscountType.PERCENT)
                .discountValue(20)
                .build();
        int discount = registry.calculateDiscount(coupon, 100_000);
        assertEquals(20_000, discount);
    }

    @Test
    void calculateDiscount_percentageWithCap() {
        Coupon coupon = Coupon.builder()
                .discountType(CouponDiscountType.PERCENT)
                .discountValue(50)
                .maxDiscountScoin(30_000)
                .build();
        int discount = registry.calculateDiscount(coupon, 100_000);
        assertEquals(30_000, discount);
    }

    @Test
    void calculateDiscount_fixedAmount() {
        Coupon coupon = Coupon.builder()
                .discountType(CouponDiscountType.FIXED)
                .discountValue(15_000)
                .build();
        int discount = registry.calculateDiscount(coupon, 100_000);
        assertEquals(15_000, discount);
    }

    @Test
    void calculateDiscount_fixedAmountExceedsGross() {
        Coupon coupon = Coupon.builder()
                .discountType(CouponDiscountType.FIXED)
                .discountValue(150_000)
                .build();
        int discount = registry.calculateDiscount(coupon, 100_000);
        assertEquals(100_000, discount);
    }
}
