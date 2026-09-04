package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Admin projection of a forum comment, including moderation information. */
@Schema(description = "Admin - bình luận forum kèm dữ liệu moderation. Không dùng cho màn hình người dùng thông thường.")
public record AdminForumCommentResponse(
        UUID commentId,
        UUID postId,
        UUID authorUserId,
        String authorFullName,
        String authorAvatarUrl,
        String authorRole,
        String content,
        @Schema(description = "Trạng thái moderation/hiển thị của bình luận.", example = "VISIBLE")
        String status,
        @Schema(description = "Số report phục vụ moderation.", example = "1")
        Integer reportCount,
        Integer reactionCount,
        Boolean reactedByCurrentUser,
        UUID replyToCommentId,
        UUID replyToUserId,
        String replyToUserName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> imageUrls
) {
}
