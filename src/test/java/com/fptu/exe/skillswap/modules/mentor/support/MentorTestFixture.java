package com.fptu.exe.skillswap.modules.mentor.support;

import java.math.BigDecimal;
import java.util.UUID;

/** Test fixture providing standardized mentor profile, service snapshots, and capability data. */
public final class MentorTestFixture {

    private MentorTestFixture() {}

    public static UUID randomMentorId() {
        return UUID.randomUUID();
    }

    public static UUID randomServiceId() {
        return UUID.randomUUID();
    }

    public static MentorProfileSnapshot createActiveMentorSnapshot(UUID userId) {
        UUID id = userId != null ? userId : UUID.randomUUID();
        return new MentorProfileSnapshot(
                id,
                "Senior Software Engineer & Architecture Mentor",
                "Specialized in backend systems, Spring Boot, and cloud architecture.",
                "ACTIVE",
                true,
                BigDecimal.valueOf(4.9),
                42
        );
    }

    public static MentorServiceSnapshot createServiceSnapshot(UUID mentorId) {
        return new MentorServiceSnapshot(
                UUID.randomUUID(),
                mentorId != null ? mentorId : UUID.randomUUID(),
                "1-on-1 Code Review & Mentorship",
                "In-depth code review and architecture guidance session.",
                60,
                50000L,
                "ONLINE",
                true
        );
    }

    public record MentorProfileSnapshot(
            UUID mentorUserId,
            String headline,
            String bio,
            String status,
            boolean isVerified,
            BigDecimal rating,
            int completedSessions
    ) {}

    public record MentorServiceSnapshot(
            UUID serviceId,
            UUID mentorUserId,
            String title,
            String description,
            int durationMinutes,
            long priceVnd,
            String deliveryMode,
            boolean isActive
    ) {}
}
