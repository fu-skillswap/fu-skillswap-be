package com.fptu.exe.skillswap.modules.blog.dto;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BlogPostDetailResponse(
        UUID id,
        String title,
        String slug,
        boolean slugLocked,
        String excerpt,
        String contentMarkdown,
        String contentHash,
        String coverImageUrl,
        String coverImageObjectKey,
        String ogImageUrl,
        String ogImageObjectKey,
        BlogAudienceType audienceType,
        BlogVisibility visibility,
        BlogPostStatus status,
        String seoTitle,
        String seoDescription,
        String canonicalUrl,
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
        Integer featuredOrder,
        LocalDateTime featuredUntil,
        LocalDateTime publishedAt,
        LocalDateTime lastPublishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version
) {
}
