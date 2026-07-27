package com.fptu.exe.skillswap.modules.blog.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Reader-safe full article. CMS and storage metadata is intentionally excluded. */
public record BlogPostReaderDetailResponse(
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
        LocalDateTime updatedAt,
        String contentMarkdown,
        String ogImageUrl,
        String seoTitle,
        String seoDescription,
        String canonicalUrl
) {
}
