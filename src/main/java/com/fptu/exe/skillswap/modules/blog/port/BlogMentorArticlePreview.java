package com.fptu.exe.skillswap.modules.blog.port;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public, immutable projection owned by Blog for mentor-profile content.
 * The contract deliberately contains no Mentor DTO or Blog entity.
 */
public record BlogMentorArticlePreview(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        Integer readingTimeMinutes,
        LocalDateTime publishedAt
) {
}
