package com.fptu.exe.skillswap.modules.mentor.port.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MentorBlogAuthorSummary(
        UUID mentorUserId,
        String fullName,
        String avatarUrl,
        String headline,
        boolean verified,
        BigDecimal averageRating,
        Integer completedSessions,
        String bookingCtaLabel
) {}
