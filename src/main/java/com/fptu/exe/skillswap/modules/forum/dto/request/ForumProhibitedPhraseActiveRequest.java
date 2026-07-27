package com.fptu.exe.skillswap.modules.forum.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ForumProhibitedPhraseActiveRequest(
        @NotNull(message = "isActive là bắt buộc")
        Boolean isActive,

        @NotNull(message = "expectedVersion là bắt buộc")
        @Min(value = 0, message = "expectedVersion không hợp lệ")
        Integer expectedVersion
) {
}
