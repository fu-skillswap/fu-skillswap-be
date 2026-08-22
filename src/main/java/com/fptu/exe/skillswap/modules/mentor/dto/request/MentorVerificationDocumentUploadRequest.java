package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Xác nhận verification document từ private upload intent do BE cấp.")
public record MentorVerificationDocumentUploadRequest(
        @Schema(
                description = "Loại minh chứng xét duyệt mentor (Bắt buộc):<br/>"
                        + "• `FPTU_AFFILIATION_PROOF`: Minh chứng sinh viên/cựu sinh viên trực thuộc FPTU (Thẻ SV, Giấy xác nhận, Bảng điểm, Bằng tốt nghiệp...). Tối đa 1 file.<br/>"
                        + "• `EXPERTISE_PROOF`: Minh chứng năng lực chuyên môn (Chứng chỉ quốc tế, giải thưởng, bảng điểm môn chuyên ngành...). Tối đa 3 file.",
                example = "FPTU_AFFILIATION_PROOF",
                allowableValues = {"FPTU_AFFILIATION_PROOF", "EXPERTISE_PROOF"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Loại tài liệu xác thực là bắt buộc")
        VerificationDocumentType documentType,

        @Schema(
                description = "Upload intent ID do API POST /documents/upload-intents trả về sau khi client đã PUT file thành công lên Storage",
                example = "e5cffe9f-3133-4d65-b5a1-9a708e1a1234",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "uploadIntentId không được để trống")
        UUID uploadIntentId
) {
}
