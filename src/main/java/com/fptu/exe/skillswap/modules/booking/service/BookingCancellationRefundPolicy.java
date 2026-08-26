package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.dto.response.BookingCancellationRefundPolicyResponse;

/** Read model for the platform-wide settlement rules already enforced by BookingService. */
public final class BookingCancellationRefundPolicy {

    private BookingCancellationRefundPolicy() {
    }

    public static BookingCancellationRefundPolicyResponse current() {
        return new BookingCancellationRefundPolicyResponse(
                (int) BookingDeadlinePolicy.CANCELLATION_EARLY_WINDOW_MINUTES,
                100, 50, 35, 15, 100, 100);
    }
}
