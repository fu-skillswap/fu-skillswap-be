package com.fptu.exe.skillswap.modules.admin.domain;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

import java.util.Locale;

public enum AdminQueueKey {
    MENTOR_VERIFICATION_PENDING_REVIEW("mentor_verification_pending_review"),
    BOOKING_UNDER_REVIEW("booking_under_review"),
    FORUM_REPORTS_OPEN("forum_reports_open"),
    PAYOUT_REQUESTS_REQUESTED("payout_requests_requested"),
    PAYMENT_ORDERS_FAILED("payment_orders_failed"),
    EMAIL_OUTBOX_FAILED("email_outbox_failed"),
    BOOKINGS_ACCEPTED_AWAITING_PAYMENT("bookings_accepted_awaiting_payment");

    private final String key;

    AdminQueueKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static AdminQueueKey parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "queueKey không hợp lệ");
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (AdminQueueKey value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        throw new BaseException(ErrorCode.BAD_REQUEST, "queueKey không hợp lệ");
    }
}
