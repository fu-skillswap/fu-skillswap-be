package com.fptu.exe.skillswap.modules.mentor.dto.response;

public record MentorBookingPolicyResponse(
        Integer minimumBookingLeadTimeMinutes,
        Integer maximumBookingHorizonDays,
        String timezone,
        Integer version
) {
}
