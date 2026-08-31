package com.fptu.exe.skillswap.shared.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Stateless conversion helpers for values persisted in the platform business
 * time zone. This is shared infrastructure, not booking domain behaviour.
 */
public final class BusinessTime {

    public static final ZoneId BUSINESS_ZONE = TimeProvider.BUSINESS_ZONE;

    private BusinessTime() {
    }

    public static LocalDateTime fromInstant(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, BUSINESS_ZONE);
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant();
    }

    public static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    public static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }
}
