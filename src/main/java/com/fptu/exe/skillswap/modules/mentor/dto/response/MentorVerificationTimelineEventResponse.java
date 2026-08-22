package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Sự kiện trong dòng thời gian xét duyệt hồ sơ")
@Builder
public record MentorVerificationTimelineEventResponse(
        @Schema(description = "ID sự kiện")
        UUID id,

        @Schema(
                description = "Loại sự kiện:<br/>"
                        + "• `REQUEST_CREATED`: Khởi tạo đơn<br/>"
                        + "• `SUBMITTED`: Nộp hồ sơ<br/>"
                        + "• `REVISION_REQUESTED`: Admin yêu cầu sửa đổi<br/>"
                        + "• `RESUBMITTED`: Nộp lại sau khi sửa<br/>"
                        + "• `APPROVED`: Phê duyệt thành công<br/>"
                        + "• `REJECTED`: Từ chối hồ sơ<br/>"
                        + "• `WITHDRAWN`: Rút hồ sơ",
                example = "SUBMITTED",
                allowableValues = {"REQUEST_CREATED", "SUBMITTED", "REVISION_REQUESTED", "RESUBMITTED", "APPROVED", "REJECTED", "WITHDRAWN"}
        )
        MentorVerificationEventType eventType,

        @Schema(description = "Trạng thái trước khi chuyển đổi")
        VerificationStatus fromStatus,

        @Schema(description = "Trạng thái sau khi chuyển đổi")
        VerificationStatus toStatus,

        @Schema(description = "Mã người thực hiện hành động")
        UUID actorUserId,

        @Schema(description = "Email người thực hiện")
        String actorEmail,

        @Schema(description = "Họ tên người thực hiện")
        String actorFullName,

        @Schema(description = "Ghi chú kèm theo sự kiện")
        String note,

        @Schema(description = "Thời điểm diễn ra sự kiện")
        LocalDateTime createdAt
) {
}

