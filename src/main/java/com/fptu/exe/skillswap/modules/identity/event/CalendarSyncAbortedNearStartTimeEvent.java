package com.fptu.exe.skillswap.modules.identity.event;

import java.util.UUID;

public record CalendarSyncAbortedNearStartTimeEvent(
        UUID bookingId,
        UUID mentorUserId,
        UUID menteeUserId
) {
}
