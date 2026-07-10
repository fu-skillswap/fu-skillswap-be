package com.fptu.exe.skillswap.modules.mentor.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record MentorAvailabilityChangedEvent(
        UUID eventId,
        UUID mentorUserId,
        UUID mentorProfileId,
        boolean previousAvailability,
        boolean currentAvailability,
        LocalDateTime occurredAt
) {
}
