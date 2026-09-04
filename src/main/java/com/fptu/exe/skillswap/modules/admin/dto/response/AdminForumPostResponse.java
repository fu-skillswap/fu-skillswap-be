package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin projection of a forum post. The JSON names intentionally match the
 * existing admin response so current admin clients remain compatible.
 */
@Schema(description = "Admin - bài viết forum kèm dữ liệu moderation. Không dùng cho màn hình người dùng thông thường.")
public record AdminForumPostResponse(
        @Schema(description = "ID bài viết.")
        UUID postId,
        @Schema(description = "ID tác giả bài viết.")
        UUID authorUserId,
        String authorFullName,
        String authorAvatarUrl,
        AdminForumProgramResponse authorProgram,
        AdminForumTopicResponse forumTopic,
        String title,
        String content,
        @Schema(description = "Trạng thái moderation/hiển thị của bài viết.", example = "PUBLISHED")
        String status,
        Integer commentCount,
        Integer reactionCount,
        @Schema(description = "Số report đang được ghi nhận cho moderation.", example = "2")
        Integer reportCount,
        LocalDateTime lastActivityAt,
        boolean reactedByCurrentUser,
        String myReactionType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> imageUrls
) {
}
