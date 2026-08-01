package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Lightweight public Blog article preview used as mentor knowledge evidence.")
public record MentorPublicArticlePreviewResponse(
        UUID id,
        String title,
        String slug,
        String excerpt,
        @Schema(nullable = true) String coverImageUrl,
        Integer readingTimeMinutes,
        LocalDateTime publishedAt
) {
}
