package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReverseMentorViolationRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
