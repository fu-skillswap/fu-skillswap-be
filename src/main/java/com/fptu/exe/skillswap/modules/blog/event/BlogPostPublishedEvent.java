package com.fptu.exe.skillswap.modules.blog.event;

import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record BlogPostPublishedEvent(
        UUID eventId,
        UUID postId,
        String slug,
        String title,
        UUID authorUserId,
        String authorName,
        BlogVisibility visibility,
        Set<UUID> categoryIds,
        Set<UUID> tagIds,
        LocalDateTime occurredAt
) {
}
