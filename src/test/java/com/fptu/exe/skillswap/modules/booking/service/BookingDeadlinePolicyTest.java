package com.fptu.exe.skillswap.modules.booking.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookingDeadlinePolicyTest {

    @Test
    void pendingExpiryUsesTheEarlierResponseOrSessionStart_LocalDateTime() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 8, 0);

        assertEquals(
                LocalDateTime.of(2026, 8, 1, 20, 0),
                BookingDeadlinePolicy.resolvePendingExpiry(createdAt, LocalDateTime.of(2026, 8, 2, 8, 0)));
        assertEquals(
                LocalDateTime.of(2026, 8, 1, 13, 0),
                BookingDeadlinePolicy.resolvePendingExpiry(createdAt, LocalDateTime.of(2026, 8, 1, 13, 0)));
    }

    @Test
    void paymentDeadlineUsesTheEarlierFourHourWindowOrSessionStart_LocalDateTime() {
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 8, 1, 8, 0);

        assertEquals(
                LocalDateTime.of(2026, 8, 1, 12, 0),
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAt, LocalDateTime.of(2026, 8, 2, 8, 0)));
        assertEquals(
                LocalDateTime.of(2026, 8, 1, 9, 30),
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAt, LocalDateTime.of(2026, 8, 1, 9, 30)));
    }

    @Test
    void pendingExpiryUsesTheEarlierResponseOrSessionStart_Instant() {
        Instant createdAtUtc = Instant.parse("2026-08-01T01:00:00Z"); // 08:00 HCM

        // 1. Far future session (+24h): response window 12h applies
        Instant sessionFarStartUtc = Instant.parse("2026-08-02T01:00:00Z"); // 08:00 HCM (+24h)
        assertEquals(
                Instant.parse("2026-08-01T13:00:00Z"), // 01:00 + 12h = 13:00 UTC (20:00 HCM)
                BookingDeadlinePolicy.resolvePendingExpiry(createdAtUtc, sessionFarStartUtc)
        );

        // 2. Last-minute session (+10m): pending request stays valid until session start
        Instant lastMinuteStartUtc = Instant.parse("2026-08-01T01:10:00Z");
        assertEquals(
                lastMinuteStartUtc,
                BookingDeadlinePolicy.resolvePendingExpiry(createdAtUtc, lastMinuteStartUtc)
        );
    }

    @Test
    void paymentDeadlineUsesTheEarlierFourHourWindowOrSessionStart_Instant() {
        Instant acceptedAtUtc = Instant.parse("2026-08-01T01:00:00Z"); // 08:00 HCM

        // 1. Far future session: 240m payment window applies (01:00 + 240m = 05:00 UTC)
        Instant sessionFarStartUtc = Instant.parse("2026-08-02T01:00:00Z");
        assertEquals(
                Instant.parse("2026-08-01T05:00:00Z"),
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAtUtc, sessionFarStartUtc)
        );

        // 2. Near session (+10m from accept): payment deadline equals session start
        Instant sessionNearStartUtc = Instant.parse("2026-08-01T01:10:00Z");
        assertEquals(
                sessionNearStartUtc,
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAtUtc, sessionNearStartUtc)
        );
    }

    @Test
    void deadlinesAreIdenticalAcrossDifferentJvmDefaultTimezones() {
        TimeZone originalTz = TimeZone.getDefault();
        try {
            // Test under UTC JVM
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Instant createdAt = Instant.parse("2026-08-01T01:00:00Z");
            Instant sessionStart = Instant.parse("2026-08-01T06:00:00Z");
            Instant deadlineUtcJvm = BookingDeadlinePolicy.resolvePendingExpiry(createdAt, sessionStart);

            // Test under Asia/Ho_Chi_Minh JVM (UTC+7)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            Instant deadlineHcmJvm = BookingDeadlinePolicy.resolvePendingExpiry(createdAt, sessionStart);

            // Test under America/New_York JVM (UTC-4/-5)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Instant deadlineNyJvm = BookingDeadlinePolicy.resolvePendingExpiry(createdAt, sessionStart);

            assertEquals(deadlineUtcJvm, deadlineHcmJvm);
            assertEquals(deadlineUtcJvm, deadlineNyJvm);
            assertEquals(Instant.parse("2026-08-01T06:00:00Z"), deadlineNyJvm);
        } finally {
            TimeZone.setDefault(originalTz);
        }
    }

    @Test
    void cancellationIsEarlyAtExactlyFourHoursAndLateBelowFourHours() {
        assertEquals(240, BookingDeadlinePolicy.CANCELLATION_EARLY_WINDOW_MINUTES);
        org.junit.jupiter.api.Assertions.assertFalse(BookingDeadlinePolicy.isLateCancellation(240));
        org.junit.jupiter.api.Assertions.assertTrue(BookingDeadlinePolicy.isLateCancellation(239));
    }
}
