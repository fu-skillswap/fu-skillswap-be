package com.fptu.exe.skillswap.modules.mentor.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MentorServiceResponse(
        UUID serviceId,
        UUID mentorUserId,
        String title,
        String description,
        Integer durationMinutes,
        boolean free,
        BigDecimal priceAmount,
        String currency,
        boolean active,
        List<MentorTagResponse> helpTopics,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
