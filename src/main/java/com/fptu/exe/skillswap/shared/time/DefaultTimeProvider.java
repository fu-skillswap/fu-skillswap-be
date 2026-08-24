package com.fptu.exe.skillswap.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Default implementation of {@link TimeProvider} backed by an injected {@link Clock}.
 */
public class DefaultTimeProvider implements TimeProvider {

    private final Clock clock;

    public DefaultTimeProvider(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public Instant instant() {
        return clock.instant();
    }

    @Override
    public OffsetDateTime offsetDateTime() {
        return offsetDateTime(BUSINESS_ZONE);
    }

    @Override
    public OffsetDateTime offsetDateTime(ZoneId zoneId) {
        return OffsetDateTime.ofInstant(clock.instant(), zoneId != null ? zoneId : BUSINESS_ZONE);
    }

    @Override
    public ZonedDateTime zonedDateTime() {
        return zonedDateTime(BUSINESS_ZONE);
    }

    @Override
    public ZonedDateTime zonedDateTime(ZoneId zoneId) {
        return ZonedDateTime.ofInstant(clock.instant(), zoneId != null ? zoneId : BUSINESS_ZONE);
    }

    @Override
    public LocalDateTime localDateTime() {
        return localDateTime(BUSINESS_ZONE);
    }

    @Override
    public LocalDateTime localDateTime(ZoneId zoneId) {
        return LocalDateTime.ofInstant(clock.instant(), zoneId != null ? zoneId : BUSINESS_ZONE);
    }

    @Override
    public LocalDate localDate() {
        return localDate(BUSINESS_ZONE);
    }

    @Override
    public LocalDate localDate(ZoneId zoneId) {
        return LocalDate.ofInstant(clock.instant(), zoneId != null ? zoneId : BUSINESS_ZONE);
    }
}
