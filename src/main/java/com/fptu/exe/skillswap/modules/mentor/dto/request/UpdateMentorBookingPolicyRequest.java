package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Partial mentor-owned scheduling preferences. Platform limits are read-only. */
public record UpdateMentorBookingPolicyRequest(
        Integer minimumBookingLeadTimeMinutes,
        Integer maximumBookingHorizonDays,
        String timezone,
        @NotNull @PositiveOrZero Integer expectedVersion
) {
}
