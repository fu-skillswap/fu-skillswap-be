package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Thông tin chi tiết tài liệu minh chứng đã tải lên")
@Builder
public record MentorVerificationDocumentResponse(
        @Schema(description = "ID tài liệu minh chứng")
        UUID id,

        @Schema(
                description = "Loại minh chứng:<br/>"
                        + "• `FPTU_AFFILIATION_PROOF`: Minh chứng sinh viên/cựu SV FPTU<br/>"
                        + "• `EXPERTISE_PROOF`: Minh chứng chuyên môn/bằng cấp",
                example = "FPTU_AFFILIATION_PROOF",
                allowableValues = {"FPTU_AFFILIATION_PROOF", "EXPERTISE_PROOF"}
        )
        VerificationDocumentType documentType,

        @Schema(
                description = "Trạng thái thẩm định tài liệu:<br/>"
                        + "• `UPLOADED`: Mới tải lên, chờ admin duyệt<br/>"
                        + "• `ACCEPTED`: Admin đã chấp thuận minh chứng này<br/>"
                        + "• `REJECTED`: Admin từ chối minh chứng này<br/>"
                        + "• `REMOVED`: Đã bị gỡ bỏ",
                example = "UPLOADED",
                allowableValues = {"UPLOADED", "ACCEPTED", "REJECTED", "REMOVED"}
        )
        VerificationDocumentStatus status,

        @Schema(
                description = "Loại lưu trữ:<br/>"
                        + "• `IMAGE`: File hình ảnh<br/>"
                        + "• `DOCUMENT`: File tài liệu (PDF)",
                example = "IMAGE",
                allowableValues = {"IMAGE", "DOCUMENT"}
        )
        VerificationStorageKind storageKind,

        @Schema(description = "Tên file gốc", example = "ConfirmationLetter_NhatTT.jpg")
        String originalFilename,

        @Schema(description = "MIME type", example = "image/jpeg")
        String contentType,

        @Schema(description = "Kích thước file (bytes)", example = "150937")
        Long sizeBytes,

        @Schema(description = "URL tạm thời để xem trước file nếu có quyền")
        String fileUrl,

        @Schema(description = "Trạng thái hoạt động của file")
        boolean isActive,

        @Schema(description = "Phiên bản tài liệu")
        Integer version,

        @Schema(description = "Ghi chú của admin khi duyệt")
        String reviewNote,

        @Schema(description = "Lý do từ chối nếu có")
        String rejectedReason,

        @Schema(description = "Thời gian tải lên")
        LocalDateTime uploadedAt
) {
}

