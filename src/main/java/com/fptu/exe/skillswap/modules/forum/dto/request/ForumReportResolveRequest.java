package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumModerationAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload admin resolve một forum report")
public record ForumReportResolveRequest(
        @NotNull(message = "action là bắt buộc")
        ForumModerationAction action,

        @Size(max = 500, message = "Ghi chú moderation không được quá 500 ký tự")
        String reviewNote
) {
}
