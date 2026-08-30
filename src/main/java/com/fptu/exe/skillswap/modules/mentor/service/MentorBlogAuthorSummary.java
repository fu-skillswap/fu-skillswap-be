package com.fptu.exe.skillswap.modules.mentor.service;

import java.math.BigDecimal;
import java.util.UUID;

public record MentorBlogAuthorSummary(
        UUID mentorUserId,
        String headline,
        boolean verified,
        BigDecimal averageRating,
        Integer completedSessions,
        String bookingCtaLabel
) {
}
