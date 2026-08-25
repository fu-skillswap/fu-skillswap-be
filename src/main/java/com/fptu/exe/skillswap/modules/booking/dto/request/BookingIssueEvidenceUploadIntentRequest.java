package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Thông tin file minh chứng dispute trước khi FE tải trực tiếp lên private storage")
public record BookingIssueEvidenceUploadIntentRequest(
        @NotBlank(message = "filename là bắt buộc")
        @Size(max = 255, message = "filename không được vượt quá 255 ký tự")
        String filename,

        @NotBlank(message = "contentType là bắt buộc")
        @Size(max = 100, message = "contentType không được vượt quá 100 ký tự")
        String contentType,

        @NotNull(message = "sizeBytes là bắt buộc")
        @Positive(message = "sizeBytes phải lớn hơn 0")
        Long sizeBytes
) {
}
