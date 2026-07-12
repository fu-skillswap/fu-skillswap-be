package com.fptu.exe.skillswap.modules.blog.dto;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Blog card response. Không chứa contentMarkdown để tránh tải nặng trên list page.")
public record BlogPostCardResponse(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        String ogImageUrl,
        BlogAudienceType audienceType,
        BlogVisibility visibility,
        BlogPostStatus status,
        BlogAuthorResponse author,
        List<BlogCategoryResponse> categories,
        List<BlogTagResponse> tags,
        Integer readingTimeMinutes,
        Long viewCount,
        boolean featured,
        Integer featuredOrder,
        LocalDateTime featuredUntil,
        LocalDateTime publishedAt,
        LocalDateTime lastPublishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
