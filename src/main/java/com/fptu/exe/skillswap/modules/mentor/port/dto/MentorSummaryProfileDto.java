package com.fptu.exe.skillswap.modules.mentor.port.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MentorSummaryProfileDto(
        boolean exists,
        String mentorStatus,
        Boolean isAvailable,
        LocalDateTime verifiedAt,
        String headline,
        BigDecimal averageRating,
        Integer totalCompletedSessions
) {}
