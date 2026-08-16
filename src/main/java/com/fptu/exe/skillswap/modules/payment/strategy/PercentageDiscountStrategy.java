package com.fptu.exe.skillswap.modules.payment.strategy;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import org.springframework.stereotype.Component;

@Component
public class PercentageDiscountStrategy implements DiscountCalculationStrategy {

    @Override
    public CouponDiscountType getSupportedDiscountType() {
        return CouponDiscountType.PERCENT;
    }

    @Override
    public int calculateDiscount(Coupon coupon, int grossScoin) {
        if (coupon == null || grossScoin <= 0) {
            return 0;
        }
        int percent = Math.max(0, coupon.getDiscountValue() == null ? 0 : coupon.getDiscountValue());
        int discount = (grossScoin * percent) / 100;
        if (coupon.getMaxDiscountScoin() != null) {
            discount = Math.min(discount, Math.max(0, coupon.getMaxDiscountScoin()));
        }
        return Math.min(discount, grossScoin);
    }
}
