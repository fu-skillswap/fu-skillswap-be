package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Payload tạo report cho post hoặc comment forum")
public record ForumReportCreateRequest(
        @NotNull(message = "targetType là bắt buộc")
        ForumReportTargetType targetType,

        @NotNull(message = "targetId là bắt buộc")
        UUID targetId,

        @NotNull(message = "reasonType là bắt buộc")
        ForumReportReasonType reasonType,

        @Size(max = 1000, message = "Mô tả report không được quá 1000 ký tự")
        String description
) {
}
