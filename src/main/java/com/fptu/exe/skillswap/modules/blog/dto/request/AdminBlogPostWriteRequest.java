package com.fptu.exe.skillswap.modules.blog.dto.request;

import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;

import java.util.List;
import java.util.UUID;

/** Shared write shape. Create and update use separate request DTOs for version semantics. */
public interface AdminBlogPostWriteRequest {
    String title();
    String slug();
    String excerpt();
    String contentMarkdown();
    String coverImageUrl();
    String coverImageObjectKey();
    String ogImageUrl();
    String ogImageObjectKey();
    BlogVisibility visibility();
    String seoTitle();
    String seoDescription();
    String canonicalUrl();
    List<UUID> categoryIds();
    List<UUID> tagIds();
}
