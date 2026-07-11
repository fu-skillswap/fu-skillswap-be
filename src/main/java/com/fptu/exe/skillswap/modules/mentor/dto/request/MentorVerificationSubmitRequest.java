package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MentorVerificationSubmitRequest(
        @Size(max = 2000, message = "Ghi chú nộp hồ sơ không được vượt quá 2000 ký tự")
        String submitNote,

        @NotNull(message = "Vui lòng xác nhận đã đọc và đồng ý với điều khoản")
        Boolean termsAccepted
) {
}
