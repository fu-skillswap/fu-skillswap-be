package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

import java.util.Set;

/** Single source of truth for mentor-service and booking-payment pricing. */
public final class PricingPolicy {

    public static final Set<Integer> ALLOWED_DURATIONS = Set.of(30, 60, 90, 120);
    public static final int MIN_PRICE_SCOIN_PER_MINUTE = 500;
    public static final int MAX_PRICE_SCOIN_PER_MINUTE = 500_000;

    private PricingPolicy() {
    }

    public static int minimumPriceForDuration(Integer durationMinutes) {
        validateDuration(durationMinutes);
        return Math.multiplyExact(durationMinutes, MIN_PRICE_SCOIN_PER_MINUTE);
    }

    public static int maximumPriceForDuration(Integer durationMinutes) {
        validateDuration(durationMinutes);
        return Math.multiplyExact(durationMinutes, MAX_PRICE_SCOIN_PER_MINUTE);
    }

    public static void validatePaidServicePrice(Integer priceScoin, Integer durationMinutes) {
        if (priceScoin == null || priceScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ có phí phải có priceScoin lớn hơn 0");
        }
        int minimum = minimumPriceForDuration(durationMinutes);
        int maximum = maximumPriceForDuration(durationMinutes);
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
        int surchargeBps = properties == null ? 0 : properties.getMenteeSurchargeBps();
        return safeInt((long) base + ((long) base * surchargeBps) / 10_000L, "Giá thanh toán vượt giới hạn hệ thống");
    }

    public static int mentorNetScoin(int basePriceScoin, PaymentProperties properties) {
        int base = Math.max(0, basePriceScoin);
        int commissionBps = properties == null ? 0 : properties.getMentorCommissionBps();
        return safeInt((long) base - ((long) base * commissionBps) / 10_000L, "Thu nhập mentor vượt giới hạn hệ thống");
    }

    /** Current product assumption: one SCoin is one VND. The rate is configurable and explicit. */
    public static long toVnd(int scoin, PaymentProperties properties) {
        int rate = properties == null ? 1 : properties.getScoinToVndRate();
        return Math.multiplyExact((long) Math.max(0, scoin), rate);
    }

    public static int toScoin(long vnd, PaymentProperties properties) {
        int rate = properties == null ? 1 : properties.getScoinToVndRate();
        if (vnd < 0 || vnd % rate != 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số tiền VND không khớp quy đổi SCoin");
        }
        return safeInt(vnd / rate, "Số SCoin vượt giới hạn hệ thống");
    }

    private static void validateDuration(Integer durationMinutes) {
        if (durationMinutes == null || !ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng dịch vụ chỉ được chọn 30, 60, 90 hoặc 120 phút");
        }
    }

    private static int safeInt(long value, String message) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, message);
        }
        return (int) value;
    }
}
