package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationUploadIntentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Trạng thái tiến trình tải file minh chứng")
public record MentorVerificationUploadIntentStatusResponse(
        @Schema(description = "Mã upload intent")
        UUID uploadIntentId,

        @Schema(
                description = "Trạng thái upload intent:<br/>"
                        + "• `PENDING_UPLOAD`: Chờ client tải file lên R2<br/>"
                        + "• `CONFIRMED`: Đã tải lên và xác nhận thành công<br/>"
                        + "• `EXPIRED`: Đã hết hạn (quá 15 phút)<br/>"
                        + "• `REJECTED`: Bị từ chối do file không hợp lệ",
                example = "PENDING_UPLOAD",
                allowableValues = {"PENDING_UPLOAD", "CONFIRMED", "EXPIRED", "REJECTED"}
        )
        MentorVerificationUploadIntentStatus status,

        @Schema(description = "Thời điểm hết hạn của upload intent")
        LocalDateTime expiresAt,

        @Schema(description = "Có thể retry tạo lại intent mới hay không")
        boolean canRetry,

        @Schema(description = "ID tài liệu sau khi đã confirm thành công")
        UUID confirmedDocumentId
) {
}

