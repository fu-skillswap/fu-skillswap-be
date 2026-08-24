package com.fptu.exe.skillswap.modules.booking.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BookingUtcDualWriteTest {

    @Test
    void booking_onCreate_synchronizesLegacyToUtcShadow() {
        LocalDateTime hcmStart = LocalDateTime.of(2026, 8, 24, 14, 0, 0); // 14:00 HCM = 07:00 UTC
        LocalDateTime hcmEnd = LocalDateTime.of(2026, 8, 24, 15, 0, 0);   // 15:00 HCM = 08:00 UTC

        Booking booking = Booking.builder()
                .selectedStartTime(hcmStart)
                .selectedEndTime(hcmEnd)
                .acceptedAt(hcmStart.minusHours(2))
                .pendingExpireAt(hcmStart.minusHours(1))
                .build();

        booking.onCreate();

        assertNotNull(booking.getCreatedAt());
        assertNotNull(booking.getCreatedAtUtc());
        assertEquals(Instant.parse("2026-08-24T07:00:00Z"), booking.getSelectedStartTimeUtc());
        assertEquals(Instant.parse("2026-08-24T08:00:00Z"), booking.getSelectedEndTimeUtc());
        assertEquals(Instant.parse("2026-08-24T05:00:00Z"), booking.getAcceptedAtUtc());
        assertEquals(Instant.parse("2026-08-24T06:00:00Z"), booking.getPendingExpireAtUtc());
    }

    @Test
    void booking_onCreate_synchronizesUtcToLegacy() {
        Instant utcStart = Instant.parse("2026-08-24T09:30:00Z"); // 09:30 UTC = 16:30 HCM
        Instant utcEnd = Instant.parse("2026-08-24T10:30:00Z");   // 10:30 UTC = 17:30 HCM

        Booking booking = Booking.builder()
                .selectedStartTimeUtc(utcStart)
                .selectedEndTimeUtc(utcEnd)
                .build();

        booking.onCreate();

        assertEquals(LocalDateTime.of(2026, 8, 24, 16, 30, 0), booking.getSelectedStartTime());
        assertEquals(LocalDateTime.of(2026, 8, 24, 17, 30, 0), booking.getSelectedEndTime());
    }

    @Test
    void slot_onCreate_synchronizesBidirectional() {
        LocalDateTime hcmStart = LocalDateTime.of(2026, 8, 25, 9, 0, 0); // 09:00 HCM = 02:00 UTC
        LocalDateTime hcmEnd = LocalDateTime.of(2026, 8, 25, 10, 0, 0);  // 10:00 HCM = 03:00 UTC

        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .startTime(hcmStart)
                .endTime(hcmEnd)
                .build();

        slot.onCreate();

        assertNotNull(slot.getCreatedAt());
        assertNotNull(slot.getCreatedAtUtc());
        assertEquals(Instant.parse("2026-08-25T02:00:00Z"), slot.getStartTimeUtc());
        assertEquals(Instant.parse("2026-08-25T03:00:00Z"), slot.getEndTimeUtc());
    }
}
