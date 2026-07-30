package com.fptu.exe.skillswap.modules.conversation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConversationLockRequest(
        @NotNull Boolean locked,
        @Size(max = 1000) String note
) {
}
