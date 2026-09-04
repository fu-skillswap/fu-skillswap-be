package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông tin để FE upload PDF trực tiếp. Upload URL có thời hạn và chỉ dùng cho tài liệu đã được backend tạo.")
public record CoursePdfUploadInitResponse(
        @Schema(description = "ID tài liệu PDF.") UUID materialId,
        @Schema(description = "URL upload tạm thời; FE dùng ngay và không lưu làm URL cố định.") String uploadUrl,
        @Schema(description = "Thời điểm URL upload hết hạn theo UTC.") Instant expiresAt,
        @Schema(description = "MIME type FE phải gửi khi upload.", example = "application/pdf") String requiredContentType,
        @Schema(description = "Metadata upload trung lập với provider; FE ưu tiên dùng object này khi có.")
        ProviderNeutralUploadMetadata uploadMetadata) {
    /** Keeps source compatibility for the original four-argument response. */
    public CoursePdfUploadInitResponse(UUID materialId, String uploadUrl, Instant expiresAt, String requiredContentType) {
        this(materialId, uploadUrl, expiresAt, requiredContentType, null);
    }
}
