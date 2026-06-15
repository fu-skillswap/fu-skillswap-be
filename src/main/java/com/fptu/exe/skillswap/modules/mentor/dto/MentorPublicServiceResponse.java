package com.fptu.exe.skillswap.modules.mentor.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record MentorPublicServiceResponse(
        UUID id,
        String title,
        String description,
        Integer durationMinutes,
        boolean free,
        BigDecimal priceAmount,
        String currency
) {
}
