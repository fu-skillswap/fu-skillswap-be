package com.fptu.exe.skillswap.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TimeProviderTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-24T10:15:30Z");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void fixedProvider_returnsExactInstantAndDerivedBusinessTime() {
        TimeProvider timeProvider = TimeProvider.fixed(FIXED_INSTANT, BUSINESS_ZONE);

        assertEquals(FIXED_INSTANT, timeProvider.instant());
        assertEquals(FIXED_INSTANT, timeProvider.now());

        LocalDateTime businessLocal = timeProvider.nowBusiness();
        assertEquals(LocalDateTime.of(2026, 8, 24, 17, 15, 30), businessLocal);

        LocalDate businessDate = timeProvider.todayBusiness();
        assertEquals(LocalDate.of(2026, 8, 24), businessDate);

        OffsetDateTime offsetDateTime = timeProvider.offsetDateTime();
        assertEquals("+07:00", offsetDateTime.getOffset().toString());
        assertEquals(17, offsetDateTime.getHour());

        ZonedDateTime zonedDateTime = timeProvider.zonedDateTime();
        assertEquals(BUSINESS_ZONE, zonedDateTime.getZone());
    }

    @Test
    void fixedUtcProvider_returnsUtcOffsetAndDateTime() {
        TimeProvider timeProvider = TimeProvider.fixedUtc(FIXED_INSTANT);

        assertEquals(FIXED_INSTANT, timeProvider.instant());
        assertEquals(OffsetDateTime.parse("2026-08-24T10:15:30Z"), timeProvider.offsetDateTime(ZoneId.of("UTC")));
        assertEquals(LocalDateTime.of(2026, 8, 24, 10, 15, 30), timeProvider.localDateTime(ZoneId.of("UTC")));
    }

    @Test
    void fromClock_wrapsClockProperly() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
        TimeProvider timeProvider = TimeProvider.from(clock);

        assertNotNull(timeProvider.getClock());
        assertEquals(FIXED_INSTANT, timeProvider.instant());
        assertEquals(LocalDateTime.of(2026, 8, 24, 17, 15, 30), timeProvider.nowBusiness());
    }
}
