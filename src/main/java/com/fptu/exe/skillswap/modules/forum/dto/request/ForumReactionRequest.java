package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumReactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload thả reaction cho bài viết forum")
public record ForumReactionRequest(
        @NotNull(message = "reactionType là bắt buộc")
        ForumReactionType reactionType
) {
}
