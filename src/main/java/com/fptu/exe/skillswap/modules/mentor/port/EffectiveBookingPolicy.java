package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

public record EffectiveBookingPolicy(
        UUID mentorUserId,
        Integer minimumBookingLeadTimeMinutes,
        Integer maximumBookingHorizonDays,
        String timezone
) {
}
