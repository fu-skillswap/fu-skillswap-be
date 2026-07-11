package com.fptu.exe.skillswap.modules.identity.event;

import java.util.UUID;

public record CalendarSyncConnectionRevokedEvent(
        UUID bookingId,
        UUID mentorUserId,
        UUID menteeUserId
) {
}
