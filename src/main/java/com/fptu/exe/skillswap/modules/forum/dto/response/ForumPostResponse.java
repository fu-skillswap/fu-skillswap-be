package com.fptu.exe.skillswap.modules.forum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Bài viết forum hiển thị cho người dùng. Trạng thái HIDDEN nghĩa là nội dung không nên hiển thị công khai.")
public record ForumPostResponse(
        @Schema(description = "ID bài viết.")
        UUID postId,
        @Schema(description = "ID tác giả; backend lấy từ người tạo bài viết.")
        UUID authorUserId,
        String authorFullName,
        String authorAvatarUrl,
        ForumProgramResponse authorProgram,
        ForumTopicResponse forumTopic,
        String title,
        String content,
        @Schema(description = "Trạng thái hiển thị của bài viết, ví dụ PUBLISHED hoặc HIDDEN.", example = "PUBLISHED")
        String status,
        @Schema(description = "Số bình luận.", example = "4")
        Integer commentCount,
        @Schema(description = "Số lượt reaction.", example = "12")
        Integer reactionCount,
        @Schema(description = "Internal field - FE người dùng không cần sử dụng. Số report phục vụ moderation.", example = "0")
        Integer reportCount,
        LocalDateTime lastActivityAt,
        boolean reactedByCurrentUser,
        String myReactionType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        java.util.List<String> imageUrls
) {
}
