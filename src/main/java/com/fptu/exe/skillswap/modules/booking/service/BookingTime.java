package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

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

    public static final ZoneId BUSINESS_ZONE = ZoneId.of(DateTimeUtil.ZONE_HCM);

    private BookingTime() {
    }

    public static LocalDateTime fromInstant(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, BUSINESS_ZONE);
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant();
    }

    public static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    public static LocalDateTime now() {
        return DateTimeUtil.now();
    }
}
