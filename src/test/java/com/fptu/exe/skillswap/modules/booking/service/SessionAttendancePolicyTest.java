package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAttendancePolicyTest {

    private final Instant start = Instant.parse("2026-09-01T08:00:00Z");
    private final Instant end = Instant.parse("2026-09-01T09:00:00Z");

    @Test
    void checkInWindow_isOpenAtStartAndClosedAtEnd() {
        assertFalse(SessionAttendancePolicy.canCheckIn(BookingStatus.PAID, SessionStatus.SCHEDULED,
                start.minusMillis(1), start, end));
        assertTrue(SessionAttendancePolicy.canCheckIn(BookingStatus.PAID, SessionStatus.SCHEDULED,
                start, start, end));
        assertTrue(SessionAttendancePolicy.canCheckIn(BookingStatus.PAID, SessionStatus.IN_PROGRESS,
                end.minusMillis(1), start, end));
        assertFalse(SessionAttendancePolicy.canCheckIn(BookingStatus.PAID, SessionStatus.IN_PROGRESS,
                end, start, end));
    }

    @Test
    void terminalOrUnconfirmedSessionsCannotCheckIn() {
        assertFalse(SessionAttendancePolicy.canCheckIn(BookingStatus.PENDING, SessionStatus.SCHEDULED,
                start, start, end));
        assertFalse(SessionAttendancePolicy.canCheckIn(BookingStatus.PAID, SessionStatus.COMPLETED,
                start, start, end));
        assertFalse(SessionAttendancePolicy.canCheckIn(BookingStatus.CANCELLED_BY_MENTEE, SessionStatus.CANCELLED,
                start, start, end));
    }
}
