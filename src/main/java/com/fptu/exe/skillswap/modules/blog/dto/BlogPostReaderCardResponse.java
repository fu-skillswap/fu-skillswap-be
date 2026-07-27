package com.fptu.exe.skillswap.modules.blog.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Reader-safe blog summary used by anonymous and authenticated reader surfaces. */
public record BlogPostReaderCardResponse(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        BlogAuthorResponse author,
        BlogAuthorConversionResponse authorConversion,
        List<BlogCategoryResponse> categories,
        List<BlogTagResponse> tags,
        Integer readingTimeMinutes,
        Long viewCount,
        Long likeCount,
        Long bookmarkCount,
        boolean likedByCurrentUser,
        boolean bookmarkedByCurrentUser,
        boolean featured,
        LocalDateTime publishedAt,
        LocalDateTime lastPublishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
