package com.fptu.exe.skillswap.modules.blog.dto;

import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Authoring projection. It excludes CMS-only SEO and storage internals. */
public record MentorBlogPostDetailResponse(
        UUID id, String title, String slug, boolean slugLocked, String excerpt, String contentMarkdown,
        String coverImageUrl, String ogImageUrl, BlogVisibility visibility, BlogPostStatus status,
        List<BlogCategoryResponse> categories, List<BlogTagResponse> tags, List<UUID> entitledServiceIds,
        boolean featured, LocalDateTime publishedAt, LocalDateTime createdAt, LocalDateTime updatedAt, Integer version
) {}
