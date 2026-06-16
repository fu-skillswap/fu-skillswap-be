package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record AdminMentorVerificationReviewRequest(
        @NotBlank(message = "Nội dung phản hồi không được để trống")
        String note
) {
}
