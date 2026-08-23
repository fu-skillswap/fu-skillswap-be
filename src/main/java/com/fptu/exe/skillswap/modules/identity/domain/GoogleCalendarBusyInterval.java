package com.fptu.exe.skillswap.modules.identity.domain;

import java.time.Instant;

/**
 * Represents a busy time interval queried from external calendars (e.g. Google Calendar FreeBusy API).
 */
public record GoogleCalendarBusyInterval(
        Instant startTime,
        Instant endTime
) {
    public boolean overlaps(Instant start, Instant end) {
        if (startTime == null || endTime == null || start == null || end == null) {
            return false;
        }
        return startTime.isBefore(end) && endTime.isAfter(start);
    }
}
