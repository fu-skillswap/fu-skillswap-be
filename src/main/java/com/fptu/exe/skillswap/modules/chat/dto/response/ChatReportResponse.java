package com.fptu.exe.skillswap.modules.chat.dto.response;

import com.fptu.exe.skillswap.modules.chat.domain.ChatReportReasonType;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReportStatus;

import java.time.Instant;
import java.util.UUID;

public record ChatReportResponse(
        UUID reportId,
        UUID conversationId,
        UUID reporterUserId,
        UUID reportedUserId,
        ChatReportReasonType reasonType,
        String description,
        ChatReportStatus status,
        UUID reviewedByUserId,
        String reviewNote,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Report resolution time as a UTC instant", example = "2026-06-24T05:00:00Z", nullable = true)
        Instant resolvedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Report creation time as a UTC instant", example = "2026-06-24T04:45:00Z")
        Instant createdAt
) {
}
