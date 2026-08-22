package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Yêu cầu tạo URL tải file minh chứng (Presigned Upload Intent)")
public record MentorVerificationDocumentUploadIntentRequest(
        @Schema(description = "Tên file gốc bao gồm phần mở rộng", example = "ConfirmationLetter_NhatTT.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String filename,

        @Schema(
                description = "MIME type của file tải lên (Bắt buộc). Chỉ chấp nhận các định dạng sau:<br/>"
                        + "• `image/jpeg` (Ảnh JPG / JPEG)<br/>"
                        + "• `image/png` (Ảnh PNG)<br/>"
                        + "• `application/pdf` (Tài liệu PDF)",
                example = "image/jpeg",
                allowableValues = {"image/jpeg", "image/png", "application/pdf"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank @Size(max = 100) String contentType,

        @Schema(description = "Dung lượng file tính bằng bytes (Tối đa 15MB = 15,728,640 bytes)", example = "150937", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive @Max(15 * 1024 * 1024) Long sizeBytes
) {}

