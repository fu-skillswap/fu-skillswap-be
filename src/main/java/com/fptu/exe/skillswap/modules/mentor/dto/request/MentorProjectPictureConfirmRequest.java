package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Yêu cầu xác nhận gắn ảnh vào dự án tiêu biểu sau khi upload lên storage")
public record MentorProjectPictureConfirmRequest(
        @Schema(description = "Mã upload intent do API tạo upload intent trả về", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "uploadIntentId không được để trống")
        UUID uploadIntentId
) {
}
