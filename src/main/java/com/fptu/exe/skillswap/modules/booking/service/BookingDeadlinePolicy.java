package com.fptu.exe.skillswap.modules.booking.service;

import java.time.LocalDateTime;

/** Shared, server-enforced deadlines for booking response and payment hold windows. */
public final class BookingDeadlinePolicy {

    public static final long PENDING_RESPONSE_WINDOW_HOURS = 12;
    public static final long PENDING_RESPONSE_PREPARATION_HOURS = 3;
    public static final long PAYMENT_WINDOW_MINUTES = 6 * 60;
    public static final long PAYMENT_PREPARATION_MINUTES = 60;

    private BookingDeadlinePolicy() {
    }

    public static LocalDateTime resolvePendingExpiry(LocalDateTime createdAt, LocalDateTime selectedStartAt) {
        if (createdAt == null || selectedStartAt == null) {
            return null;
        }
        LocalDateTime responseDeadline = createdAt.plusHours(PENDING_RESPONSE_WINDOW_HOURS);
        LocalDateTime preparationDeadline = selectedStartAt.minusHours(PENDING_RESPONSE_PREPARATION_HOURS);
        return responseDeadline.isBefore(preparationDeadline) ? responseDeadline : preparationDeadline;
    }

    public static LocalDateTime resolvePaymentDeadline(LocalDateTime acceptedAt, LocalDateTime selectedStartAt) {
        if (acceptedAt == null) {
            return selectedStartAt == null ? null : selectedStartAt.minusMinutes(PAYMENT_PREPARATION_MINUTES);
        }
        LocalDateTime paymentWindowDeadline = acceptedAt.plusMinutes(PAYMENT_WINDOW_MINUTES);
        if (selectedStartAt == null) {
            return paymentWindowDeadline;
        }
        LocalDateTime preparationDeadline = selectedStartAt.minusMinutes(PAYMENT_PREPARATION_MINUTES);
        return paymentWindowDeadline.isBefore(preparationDeadline) ? paymentWindowDeadline : preparationDeadline;
    }
}
