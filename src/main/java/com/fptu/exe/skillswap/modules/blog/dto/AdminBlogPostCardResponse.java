package com.fptu.exe.skillswap.modules.blog.dto;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Lightweight CMS list projection. It deliberately excludes article body and storage keys. */
public record AdminBlogPostCardResponse(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        BlogAuthorResponse author,
        List<BlogCategoryResponse> categories,
        List<BlogTagResponse> tags,
        Integer readingTimeMinutes,
        Long viewCount,
        Long likeCount,
        Long bookmarkCount,
        boolean featured,
        LocalDateTime publishedAt,
        LocalDateTime lastPublishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        BlogPostStatus status,
        BlogVisibility visibility,
        BlogAuthorType authorType,
        Integer featuredOrder,
        LocalDateTime featuredUntil,
        Integer version,
        boolean deleted,
        LocalDateTime deletedAt
) {
}
