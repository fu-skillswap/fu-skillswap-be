package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Học vấn hoặc giải thưởng nổi bật của mentor")
public record MentorAchievementResponse(
        UUID id,
        String title,
        String awardDescription,
        LocalDate achievedAt,
        String productHeader,
        String productDescription,
        String demoUrl,
        Integer displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
