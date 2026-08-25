package com.fptu.exe.skillswap.modules.booking.domain;

/** Test-only escape hatch for arranging persisted legacy states. */
public final class BookingStateTestSupport {

    private BookingStateTestSupport() {
    }

    public static void setStatus(Booking booking, BookingStatus status) {
        booking.setStatus(status);
    }
}
