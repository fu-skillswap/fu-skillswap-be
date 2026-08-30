package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * @deprecated Moved to {@link com.fptu.exe.skillswap.shared.time.BookingTime}.
 */
@Deprecated(forRemoval = true)
public final class BookingTime {

    public static final ZoneId BUSINESS_ZONE = com.fptu.exe.skillswap.shared.time.BookingTime.BUSINESS_ZONE;

    private BookingTime() {
    }

    public static LocalDateTime fromInstant(Instant value) {
        return com.fptu.exe.skillswap.shared.time.BookingTime.fromInstant(value);
    }

    public static Instant toInstant(LocalDateTime value) {
        return com.fptu.exe.skillswap.shared.time.BookingTime.toInstant(value);
    }

    public static OffsetDateTime toOffsetDateTime(Instant value) {
        return com.fptu.exe.skillswap.shared.time.BookingTime.toOffsetDateTime(value);
    }

    public static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return com.fptu.exe.skillswap.shared.time.BookingTime.toOffsetDateTime(value);
    }

    public static Instant resolveSelectedStartUtc(Booking booking) {
        return com.fptu.exe.skillswap.shared.time.BookingTime.resolveSelectedStartUtc(booking);
    }

    public static Instant resolveSelectedEndUtc(Booking booking) {
        return com.fptu.exe.skillswap.shared.time.BookingTime.resolveSelectedEndUtc(booking);
    }

}
