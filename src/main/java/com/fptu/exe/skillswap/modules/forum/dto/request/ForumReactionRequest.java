package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumReactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Thêm hoặc bỏ reaction cho bài viết forum. Backend lấy người dùng và bài viết từ tài khoản/path hiện tại.")
public record ForumReactionRequest(
        @NotNull(message = "reactionType là bắt buộc")
        @Schema(description = "Loại reaction người dùng chọn.", example = "LIKE", requiredMode = Schema.RequiredMode.REQUIRED)
        ForumReactionType reactionType
) {
}
