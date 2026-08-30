package com.fptu.exe.skillswap.modules.blog.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MentorPublicArticlePreviewResponse(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        Integer readingTimeMinutes,
        LocalDateTime publishedAt
) {}
