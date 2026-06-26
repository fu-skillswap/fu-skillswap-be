package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Bộ lọc danh sách forum posts cho admin")
public record AdminForumPostListRequest(
        Integer page,
        Integer size,
        String keyword,
        UUID helpTopicId,
        UUID authorId,
        ForumPostStatus status
) {
}
