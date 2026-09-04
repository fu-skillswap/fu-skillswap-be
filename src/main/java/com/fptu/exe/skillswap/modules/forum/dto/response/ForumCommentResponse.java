package com.fptu.exe.skillswap.modules.forum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Bình luận forum hiển thị cho người dùng. Bình luận HIDDEN không nên hiển thị nội dung công khai.")
public record ForumCommentResponse(
        @Schema(description = "ID bình luận.")
        UUID commentId,
        @Schema(description = "ID bài viết chứa bình luận.")
        UUID postId,
        @Schema(description = "ID tác giả; backend lấy từ tài khoản đăng nhập.")
        UUID authorUserId,
        String authorFullName,
        String authorAvatarUrl,
        String authorRole,
        String content,
        @Schema(description = "Trạng thái hiển thị, ví dụ VISIBLE hoặc HIDDEN.", example = "VISIBLE")
        String status,
        @Schema(description = "Internal field - FE người dùng không cần sử dụng. Số report phục vụ moderation.", example = "0")
        Integer reportCount,
        Integer reactionCount,
        Boolean reactedByCurrentUser,
        UUID replyToCommentId,
        UUID replyToUserId,
        String replyToUserName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        java.util.List<String> imageUrls
) {
}
