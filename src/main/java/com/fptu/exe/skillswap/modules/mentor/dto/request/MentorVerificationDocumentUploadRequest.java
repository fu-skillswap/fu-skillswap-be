package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Xác nhận verification document từ private upload intent do BE cấp.")
public record MentorVerificationDocumentUploadRequest(
        @Schema(description = "Loại minh chứng", example = "FPTU_AFFILIATION_PROOF")
        @NotNull(message = "Loại tài liệu xác thực là bắt buộc")
        VerificationDocumentType documentType,

        @Schema(description = "Upload intent ID do API upload-intents trả về")
        @NotNull(message = "uploadIntentId không được để trống")
        UUID uploadIntentId
) {
}
