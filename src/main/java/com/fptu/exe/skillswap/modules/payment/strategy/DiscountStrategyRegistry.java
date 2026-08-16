package com.fptu.exe.skillswap.modules.payment.strategy;

import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DiscountStrategyRegistry {

    private final Map<CouponDiscountType, DiscountCalculationStrategy> strategies = new EnumMap<>(CouponDiscountType.class);

    public DiscountStrategyRegistry(List<DiscountCalculationStrategy> strategyList) {
        if (strategyList != null) {
            for (DiscountCalculationStrategy strategy : strategyList) {
                if (strategy.getSupportedDiscountType() != null) {
                    strategies.put(strategy.getSupportedDiscountType(), strategy);
                }
            }
        }
    }

    public int calculateDiscount(Coupon coupon, int grossScoin) {
        if (coupon == null || coupon.getDiscountType() == null || grossScoin <= 0) {
            return 0;
        }
        DiscountCalculationStrategy strategy = strategies.get(coupon.getDiscountType());
        if (strategy == null) {
            return 0;
        }
        return strategy.calculateDiscount(coupon, grossScoin);
    }
}
