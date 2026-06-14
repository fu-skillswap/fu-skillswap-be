package com.fptu.exe.skillswap.modules.mentor.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record MentorRecommendationResponse(
        MentorDiscoveryCardResponse mentor,
        BigDecimal matchScore,
        List<String> matchReasons
) {
}
