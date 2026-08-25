package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.shared.time.TimeProvider;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTimeConfigurationTest {

    @Test
    void resolvedClock_isAlsoUsedByLegacyEntityTimestampFacade() {
        Clock originalClock = DateTimeUtil.getClock();
        Instant fixedInstant = Instant.parse("2026-09-01T03:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        try {
            ApplicationTimeConfiguration configuration = new ApplicationTimeConfiguration();
            TimeProvider timeProvider = configuration.timeProvider(fixedClock);

            assertEquals(fixedInstant, timeProvider.instant());
            assertEquals(fixedInstant, DateTimeUtil.instantNow());
        } finally {
            DateTimeUtil.setClock(originalClock);
        }
    }
}
