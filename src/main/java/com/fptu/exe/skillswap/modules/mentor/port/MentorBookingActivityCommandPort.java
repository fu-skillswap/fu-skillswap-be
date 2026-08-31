package com.fptu.exe.skillswap.modules.mentor.port;

import java.time.Instant;
import java.util.UUID;

/** Applies booking lifecycle facts to the mentor aggregate without exposing it. */
public interface MentorBookingActivityCommandPort {

    void recordMentorActivity(UUID mentorUserId, Instant occurredAtUtc);

    void recordCompletedSession(UUID mentorUserId, Instant completedAtUtc);
}
