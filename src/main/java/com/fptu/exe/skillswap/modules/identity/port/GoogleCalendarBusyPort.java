package com.fptu.exe.skillswap.modules.identity.port;

import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarBusyInterval;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GoogleCalendarBusyPort {

    /**
     * Queries busy time intervals for the given mentor within [timeMin, timeMax].
     * Returns an empty list if the mentor has not connected Google Calendar. A connected calendar
     * that cannot be checked must throw so callers can choose fail-soft or fail-closed behavior.
     */
    List<GoogleCalendarBusyInterval> queryBusyIntervals(UUID mentorUserId, Instant timeMin, Instant timeMax);
}
