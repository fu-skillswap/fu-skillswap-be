package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Metadata của file đã upload bằng presigned URL do BE cấp. Backend xác minh object trực tiếp trên private storage trước khi lưu.")
public record MentorVerificationDocumentUploadRequest(
        @Schema(description = "Loại minh chứng", example = "FPTU_AFFILIATION_PROOF")
        @NotNull(message = "Loại tài liệu xác thực là bắt buộc")
        VerificationDocumentType documentType,

        @Schema(example = "skillswap/verification-documents/mentor-verification/019f1234/fptu_affiliation_proof/019f5678.jpg", description = "Object key được BE trả về từ API presigned upload")
        @NotBlank(message = "objectKey không được để trống")
        @Size(max = 500, message = "objectKey không được vượt quá 500 ký tự")
        String objectKey,

        @Schema(example = "proof.pdf")
        @NotBlank(message = "Tên file gốc không được để trống")
        @Size(max = 255, message = "Tên file gốc không được vượt quá 255 ký tự")
        String originalFilename,

        @Schema(example = "image/jpeg", description = "Chỉ hỗ trợ image/jpeg, image/jpg, image/png hoặc application/pdf")
        @NotBlank(message = "Loại nội dung file không được để trống")
        @Size(max = 100, message = "Loại nội dung file không được vượt quá 100 ký tự")
        String contentType,

        @Schema(example = "245678", nullable = true)
        @NotNull(message = "Kích thước file không được để trống")
        Long sizeBytes
) {
}
