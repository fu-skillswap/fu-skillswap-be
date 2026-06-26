package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Bộ lọc danh sách forum comments cho admin")
public record AdminForumCommentListRequest(
        Integer page,
        Integer size,
        String keyword,
        UUID postId,
        UUID authorId,
        ForumCommentStatus status
) {
}
