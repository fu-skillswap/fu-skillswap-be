package com.fptu.exe.skillswap.modules.identity.event;

import java.util.UUID;

public record CalendarSyncFailedEvent(
        UUID bookingId,
        UUID mentorUserId,
        UUID menteeUserId,
        String errorCode,
        String errorMessage
) {
}
