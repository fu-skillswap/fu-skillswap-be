package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;

/** Single source of truth for mentor-service and booking-payment pricing. */
public final class PricingPolicy {

    public static final int BPS_DENOMINATOR = 10_000;

    private PricingPolicy() {
    }

    public static Set<Integer> allowedServiceDurations(PaymentProperties properties) {
        PaymentProperties requiredProperties = requireProperties(properties);
        return Collections.unmodifiableSet(new LinkedHashSet<>(requiredProperties.getAllowedServiceDurationsMinutes()));
    }

    public static boolean isAllowedServiceDuration(Integer durationMinutes, PaymentProperties properties) {
        return durationMinutes != null && allowedServiceDurations(properties).contains(durationMinutes);
    }

    public static int minimumPriceForDuration(Integer durationMinutes, PaymentProperties properties) {
        validateDuration(durationMinutes, properties);
        return Math.multiplyExact(durationMinutes, requireProperties(properties).getMinPriceScoinPerMinute());
    }

    public static int maximumPriceForDuration(Integer durationMinutes, PaymentProperties properties) {
        validateDuration(durationMinutes, properties);
        return Math.multiplyExact(durationMinutes, requireProperties(properties).getMaxPriceScoinPerMinute());
    }

    public static void validatePaidServicePrice(Integer priceScoin,
                                                Integer durationMinutes,
                                                PaymentProperties properties) {
        if (priceScoin == null || priceScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ có phí phải có priceScoin lớn hơn 0");
        }
        int minimum = minimumPriceForDuration(durationMinutes, properties);
        int maximum = maximumPriceForDuration(durationMinutes, properties);
        if (priceScoin < minimum) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Dịch vụ có phí phải có giá tối thiểu " + minimum + " SCoin cho " + durationMinutes + " phút");
        }
        if (priceScoin > maximum) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Dịch vụ có phí chỉ được đặt tối đa " + maximum + " SCoin cho " + durationMinutes + " phút");
        }
    }

    public static int menteePayableScoin(int basePriceScoin, PaymentProperties properties) {
        int base = Math.max(0, basePriceScoin);
        int surchargeBps = requireProperties(properties).getMenteeSurchargeBps();
        return safeInt((long) base + bpsAmount(base, surchargeBps), "Giá thanh toán vượt giới hạn hệ thống");
    }

    public static int mentorNetScoin(int basePriceScoin, PaymentProperties properties) {
        int base = Math.max(0, basePriceScoin);
        int commissionBps = requireProperties(properties).getMentorCommissionBps();
        return safeInt((long) base - bpsAmount(base, commissionBps), "Thu nhập mentor vượt giới hạn hệ thống");
    }

    /** Current product assumption: one SCoin is one VND. The rate is configurable and explicit. */
    public static long toVnd(int scoin, PaymentProperties properties) {
        int rate = requireProperties(properties).getScoinToVndRate();
        return Math.multiplyExact((long) Math.max(0, scoin), rate);
    }

    public static int toScoin(long vnd, PaymentProperties properties) {
        int rate = requireProperties(properties).getScoinToVndRate();
        if (vnd < 0 || vnd % rate != 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số tiền VND không khớp quy đổi SCoin");
        }
        return safeInt(vnd / rate, "Số SCoin vượt giới hạn hệ thống");
    }

    public static int bpsAmount(int amount, int bps) {
        return safeInt(((long) Math.max(0, amount) * Math.max(0, bps)) / BPS_DENOMINATOR,
                "Số tiền tính theo tỷ lệ vượt giới hạn hệ thống");
    }

    private static void validateDuration(Integer durationMinutes, PaymentProperties properties) {
        if (!isAllowedServiceDuration(durationMinutes, properties)) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Thời lượng dịch vụ không thuộc cấu hình cho phép: " + allowedServiceDurations(properties));
        }
    }

    private static PaymentProperties requireProperties(PaymentProperties properties) {
        if (properties == null) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình application.payment");
        }
        return properties;
    }

    private static int safeInt(long value, String message) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, message);
        }
        return (int) value;
    }
}
