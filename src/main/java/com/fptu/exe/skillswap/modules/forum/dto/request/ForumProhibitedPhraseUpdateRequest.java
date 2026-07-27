package com.fptu.exe.skillswap.modules.forum.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForumProhibitedPhraseUpdateRequest(
        @NotBlank(message = "Cụm từ cấm không được để trống")
        @Size(max = 200, message = "Cụm từ cấm không được quá 200 ký tự")
        String phrase,

        @NotNull(message = "expectedVersion là bắt buộc")
        @Min(value = 0, message = "expectedVersion không hợp lệ")
        Integer expectedVersion
) {
}
