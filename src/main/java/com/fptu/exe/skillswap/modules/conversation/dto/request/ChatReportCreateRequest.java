package com.fptu.exe.skillswap.modules.conversation.dto.request;

import com.fptu.exe.skillswap.modules.conversation.domain.ChatReportReasonType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatReportCreateRequest(
        @NotNull ChatReportReasonType reasonType,
        @Size(max = 1000) String description
) {
}
