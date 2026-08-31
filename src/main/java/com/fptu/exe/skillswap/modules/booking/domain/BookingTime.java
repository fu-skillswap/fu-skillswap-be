package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.time.BusinessTime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Boundary conversion for booking timestamps.
 *
 * <p>The booking schema still stores {@link LocalDateTime}; those values use the
 * platform business zone. Keeping conversion here prevents comparing UTC local
 * values with Asia/Ho_Chi_Minh local values.</p>
 */
public final class BookingTime {

    public static final ZoneId BUSINESS_ZONE = BusinessTime.BUSINESS_ZONE;

    private BookingTime() {
    }

    public static LocalDateTime fromInstant(Instant value) {
        return BusinessTime.fromInstant(value);
    }

    public static Instant toInstant(LocalDateTime value) {
        return BusinessTime.toInstant(value);
    }

    public static OffsetDateTime toOffsetDateTime(Instant value) {
        return BusinessTime.toOffsetDateTime(value);
    }

    public static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return BusinessTime.toOffsetDateTime(value);
    }

    public static Instant resolveSelectedStartUtc(Booking booking) {
        if (booking == null) {
            return null;
        }
        if (booking.getSelectedStartTimeUtc() != null) {
            return booking.getSelectedStartTimeUtc();
        }
        if (booking.getSlot() != null && booking.getSlot().getStartTimeUtc() != null) {
            return booking.getSlot().getStartTimeUtc();
        }
        if (booking.getSelectedStartTime() != null) {
            return toInstant(booking.getSelectedStartTime());
        }
        return booking.getSlot() != null ? toInstant(booking.getSlot().getStartTime()) : null;
    }

    public static Instant resolveSelectedEndUtc(Booking booking) {
        if (booking == null) {
            return null;
        }
        if (booking.getSelectedEndTimeUtc() != null) {
            return booking.getSelectedEndTimeUtc();
        }
        if (booking.getSlot() != null && booking.getSlot().getEndTimeUtc() != null) {
            return booking.getSlot().getEndTimeUtc();
        }
        if (booking.getSelectedEndTime() != null) {
            return toInstant(booking.getSelectedEndTime());
        }
        return booking.getSlot() != null ? toInstant(booking.getSlot().getEndTime()) : null;
    }

}
