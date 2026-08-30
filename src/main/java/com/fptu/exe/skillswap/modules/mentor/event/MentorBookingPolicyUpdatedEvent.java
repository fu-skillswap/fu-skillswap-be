package com.fptu.exe.skillswap.modules.mentor.event;

import java.util.UUID;

public record MentorBookingPolicyUpdatedEvent(
        UUID mentorUserId
) {}
