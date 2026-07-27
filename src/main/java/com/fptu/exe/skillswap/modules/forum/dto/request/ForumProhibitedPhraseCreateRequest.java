package com.fptu.exe.skillswap.modules.forum.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForumProhibitedPhraseCreateRequest(
        @NotBlank(message = "Cụm từ cấm không được để trống")
        @Size(max = 200, message = "Cụm từ cấm không được quá 200 ký tự")
        String phrase
) {
}
