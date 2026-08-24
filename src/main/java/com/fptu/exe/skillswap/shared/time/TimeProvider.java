package com.fptu.exe.skillswap.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Injectable time provider for application time across services and components.
 *
 * <p>Production applications receive a UTC {@link Clock} bean by default.
 * Services can obtain UTC instants or convert to the platform business zone
 * ({@code Asia/Ho_Chi_Minh}). Unit tests can inject deterministic instances via
 * {@link #from(Clock)}, {@link #fixed(Instant, ZoneId)}, or {@link #fixedUtc(Instant)}.</p>
 */
public interface TimeProvider {

    ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    ZoneId UTC_ZONE = ZoneId.of("UTC");

    Clock getClock();

    Instant instant();

    default Instant now() {
        return instant();
    }

    OffsetDateTime offsetDateTime();

    OffsetDateTime offsetDateTime(ZoneId zoneId);

    default OffsetDateTime offsetNow() {
        return offsetDateTime();
    }

    ZonedDateTime zonedDateTime();

    ZonedDateTime zonedDateTime(ZoneId zoneId);

    LocalDateTime localDateTime();

    LocalDateTime localDateTime(ZoneId zoneId);

    default LocalDateTime nowBusiness() {
        return localDateTime(BUSINESS_ZONE);
    }

    LocalDate localDate();

    LocalDate localDate(ZoneId zoneId);

    default LocalDate todayBusiness() {
        return localDate(BUSINESS_ZONE);
    }

    static TimeProvider from(Clock clock) {
        return new DefaultTimeProvider(clock);
    }

    static TimeProvider fixed(Instant instant, ZoneId zoneId) {
        return new DefaultTimeProvider(Clock.fixed(instant, zoneId != null ? zoneId : BUSINESS_ZONE));
    }

    static TimeProvider fixedUtc(Instant instant) {
        return new DefaultTimeProvider(Clock.fixed(instant, UTC_ZONE));
    }
}
