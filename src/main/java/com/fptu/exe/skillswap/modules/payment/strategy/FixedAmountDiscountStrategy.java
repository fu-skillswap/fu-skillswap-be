package com.fptu.exe.skillswap.modules.payment.strategy;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import org.springframework.stereotype.Component;

@Component
public class FixedAmountDiscountStrategy implements DiscountCalculationStrategy {

    @Override
    public CouponDiscountType getSupportedDiscountType() {
        return CouponDiscountType.FIXED;
    }

    @Override
    public int calculateDiscount(Coupon coupon, int grossScoin) {
        if (coupon == null || grossScoin <= 0) {
            return 0;
        }
        int fixedValue = Math.max(0, coupon.getDiscountValue() == null ? 0 : coupon.getDiscountValue());
        return Math.min(fixedValue, grossScoin);
    }
}
