package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Dự án tiêu biểu của mentor")
public record MentorFeaturedProjectResponse(
        UUID id,
        String title,
        String pictureUrl,
        String content,
        String projectDescription,
        String liveDemoUrl,
        Integer displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
