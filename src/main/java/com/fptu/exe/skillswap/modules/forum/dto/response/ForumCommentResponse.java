package com.fptu.exe.skillswap.modules.forum.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ForumCommentResponse(
        UUID commentId,
        UUID postId,
        UUID authorUserId,
        String authorFullName,
        String authorAvatarUrl,
        String authorRole,
        String content,
        String status,
        Integer reportCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        java.util.List<String> imageUrls
) {
}
