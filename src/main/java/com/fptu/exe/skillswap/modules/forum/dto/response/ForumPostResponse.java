package com.fptu.exe.skillswap.modules.forum.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ForumPostResponse(
        UUID postId,
        UUID authorUserId,
        String authorFullName,
        String authorAvatarUrl,
        ForumHelpTopicResponse helpTopic,
        String title,
        String content,
        String status,
        Integer commentCount,
        Integer reactionCount,
        Integer reportCount,
        LocalDateTime lastActivityAt,
        boolean reactedByCurrentUser,
        String myReactionType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
