package com.fptu.exe.skillswap.modules.booking.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Minimal group-event context embedded in a seat booking response. */
public record GroupSessionBookingSummaryResponse(
        UUID groupSessionId,
        String serviceTitle,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime registrationClosesAt
) {}
