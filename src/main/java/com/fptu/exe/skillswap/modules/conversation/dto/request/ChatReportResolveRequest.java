package com.fptu.exe.skillswap.modules.conversation.dto.request;

import com.fptu.exe.skillswap.modules.conversation.domain.ChatReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatReportResolveRequest(
        @NotNull ChatReportStatus status,
        @Size(max = 1000) String reviewNote
) {
}
