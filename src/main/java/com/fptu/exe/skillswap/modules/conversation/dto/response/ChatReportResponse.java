package com.fptu.exe.skillswap.modules.conversation.dto.response;

import com.fptu.exe.skillswap.modules.conversation.domain.ChatReportReasonType;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatReportStatus;

import java.time.LocalDateTime;
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
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
}
