package com.fptu.exe.skillswap.modules.payment.strategy;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;

public interface DiscountCalculationStrategy {

    CouponDiscountType getSupportedDiscountType();

    int calculateDiscount(Coupon coupon, int grossScoin);
}
