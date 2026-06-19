package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Thông tin minh chứng mentor đã upload sẵn lên Cloudinary")
public record MentorVerificationDocumentUploadRequest(
        @Schema(description = "Loại minh chứng", example = "FPTU_AFFILIATION_PROOF")
        @NotNull(message = "Loại tài liệu xác thực là bắt buộc")
        VerificationDocumentType documentType,

        @Schema(example = "https://res.cloudinary.com/demo/image/upload/v12345/mentor-verification/proof.jpg")
        @NotBlank(message = "Đường dẫn tài liệu không được để trống")
        @Size(max = 2000, message = "Đường dẫn tài liệu không được vượt quá 2000 ký tự")
        String fileUrl,

        @Schema(example = "mentor-verification/user-123/proof_abc123", description = "Cloudinary public ID của file")
        @NotBlank(message = "Mã publicId của tài liệu không được để trống")
        @Size(max = 500, message = "Mã publicId của tài liệu không được vượt quá 500 ký tự")
        String publicId,

        @Schema(example = "proof.jpg", nullable = true)
        @Size(max = 255, message = "Tên file gốc không được vượt quá 255 ký tự")
        String originalFilename,

        @Schema(example = "image/jpeg")
        @NotBlank(message = "Loại nội dung file không được để trống")
        @Size(max = 100, message = "Loại nội dung file không được vượt quá 100 ký tự")
        String contentType,

        @Schema(example = "245678", nullable = true)
        @NotNull(message = "Kích thước file không được để trống")
        Long sizeBytes
) {
}
