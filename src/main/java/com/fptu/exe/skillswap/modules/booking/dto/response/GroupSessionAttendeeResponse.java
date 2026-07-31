package com.fptu.exe.skillswap.modules.booking.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record GroupSessionAttendeeResponse(
        UUID bookingId,
        UUID userId,
        String displayName,
        String avatarUrl,
        String attendeeState,
        LocalDateTime joinedAt
) {}
