package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** Admin projection of a forum report and its resolution audit data. */
@Schema(description = "Admin - report forum dùng cho queue moderation và xử lý kết quả. Không dùng cho FE người dùng.")
public record AdminForumReportResponse(
        UUID reportId,
        String targetType,
        UUID targetId,
        String targetStatus,
        String targetTitle,
        String targetContentPreview,
        UUID targetAuthorUserId,
        String targetAuthorFullName,
        UUID reporterUserId,
        String reporterFullName,
        String reasonType,
        String description,
        String status,
        UUID reviewedByUserId,
        String reviewNote,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}
