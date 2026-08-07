package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record MentorMatchingContext(
        UUID menteeUserId,
        StudentProfile menteeProfile,
        MenteeMatchingFeatures matchingFeatures,
        LocalDateTime evaluatedAt,
        String algorithmVersion
) {
    public MentorMatchingContext {
        Objects.requireNonNull(menteeUserId, "menteeUserId must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion must not be blank");
        }
        algorithmVersion = algorithmVersion.trim();
    }
}
