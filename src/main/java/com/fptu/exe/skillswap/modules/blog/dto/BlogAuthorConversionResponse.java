package com.fptu.exe.skillswap.modules.blog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BlogAuthorConversionResponse(
        UUID mentorUserId,
        String headline,
        boolean verifiedMentor,
        BigDecimal averageRating,
        Integer completedSessions,
        String primaryCtaLabel,
        String profilePath
) {
}
