package com.fptu.exe.skillswap.modules.mentor.dto;

import jakarta.validation.constraints.Size;

public record MentorVerificationSubmitRequest(
        @Size(max = 2000, message = "Ghi chú nộp hồ sơ không được vượt quá 2000 ký tự")
        String submitNote,

        Boolean termsAccepted
) {
}
