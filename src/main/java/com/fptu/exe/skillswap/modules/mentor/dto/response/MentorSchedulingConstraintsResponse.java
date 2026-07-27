package com.fptu.exe.skillswap.modules.mentor.dto.response;

/** Server-controlled limits. These values are informational and are never PATCH-able by mentors. */
public record MentorSchedulingConstraintsResponse(
        int maximumAvailabilityQueryDays,
        int maximumParentSlotDurationMinutes
) {
}
