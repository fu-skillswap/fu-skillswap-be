package com.fptu.exe.skillswap.modules.blog.dto.request;

import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MentorBlogPostUpdateRequest(
        @NotNull @PositiveOrZero Integer expectedVersion,
        @NotBlank @Size(max = 220) String title,
        String excerpt,
        String contentMarkdown,
        UUID coverAssetId,
        UUID ogAssetId,
        BlogVisibility visibility,
        List<UUID> categoryIds,
        List<UUID> tagIds,
        List<UUID> entitledServiceIds
) {}
