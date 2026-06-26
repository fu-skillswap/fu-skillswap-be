package com.fptu.exe.skillswap.modules.forum.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ForumReportResponse(
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
