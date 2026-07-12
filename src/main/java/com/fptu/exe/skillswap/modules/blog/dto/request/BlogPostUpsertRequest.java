package com.fptu.exe.skillswap.modules.blog.dto.request;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BlogPostUpsertRequest(
        @NotBlank @Size(max = 220) String title,
        @Size(max = 240) String slug,
        String excerpt,
        String contentMarkdown,
        String coverImageUrl,
        String coverImageObjectKey,
        String ogImageUrl,
        String ogImageObjectKey,
        BlogAudienceType audienceType,
        BlogVisibility visibility,
        @Size(max = 220) String seoTitle,
        @Size(max = 320) String seoDescription,
        String canonicalUrl,
        List<UUID> categoryIds,
        List<UUID> tagIds
) {
}
