package com.fptu.exe.skillswap.modules.mentor.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MentorDiscoveryQueryRow(
        UUID mentorUserId,
        String displayName,
        String avatarUrl,
        String headline,
        String expertiseDescription,
        String bio,
        Integer foundationSupportLevel,
        Integer outputReviewSupportLevel,
        Integer directionSupportLevel,
        Boolean isAvailable,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        LocalDateTime verifiedAt,
        Integer acceptedBookingCount,
        Integer rejectedBookingCount,
        Integer mentorCancelledBookingCount,
        LocalDateTime lastActiveAt,
        Double matchScore
) {
}
