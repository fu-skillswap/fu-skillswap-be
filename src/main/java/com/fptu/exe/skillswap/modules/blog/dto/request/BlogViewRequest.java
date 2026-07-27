package com.fptu.exe.skillswap.modules.blog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogViewRequest(
        @NotBlank @Size(max = 128) String sessionId
) {
}
