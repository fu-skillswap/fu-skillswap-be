package com.fptu.exe.skillswap.modules.blog.dto.request;

import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Admin may moderate placement and safety metadata, never mentor authored content. */
public record AdminMentorBlogModerationRequest(
        @NotNull @PositiveOrZero Integer expectedVersion,
        BlogVisibility visibility,
        List<UUID> categoryIds,
        List<UUID> tagIds,
        @Size(max = 220) String seoTitle,
        @Size(max = 320) String seoDescription,
        Boolean archive
) {}
