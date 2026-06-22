package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record AdminMentorVerificationReviewRequest(
        @NotBlank(message = "Nội dung phản hồi không được để trống")
        @Size(max = 2000, message = "Nội dung phản hồi không được vượt quá 2000 ký tự")
        String note
) {
}
