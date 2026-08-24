package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMachine;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;

import java.time.Instant;

/** Pure policy for attendance. All comparisons are UTC instants supplied by the caller. */
public final class SessionAttendancePolicy {

    private SessionAttendancePolicy() {
    }

    public static boolean canCheckIn(BookingStatus bookingStatus,
                                     SessionStatus sessionStatus,
                                     Instant nowUtc,
                                     Instant scheduledStartUtc,
                                     Instant scheduledEndUtc) {
        if (!BookingStateMachine.isScheduled(bookingStatus)
                || (sessionStatus != SessionStatus.SCHEDULED && sessionStatus != SessionStatus.IN_PROGRESS)
                || nowUtc == null || scheduledStartUtc == null || scheduledEndUtc == null) {
            return false;
        }
        return !nowUtc.isBefore(scheduledStartUtc) && nowUtc.isBefore(scheduledEndUtc);
    }
}
