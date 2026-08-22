package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Thông tin chi tiết yêu cầu xét duyệt hồ sơ Mentor")
@Builder
public record MentorVerificationRequestResponse(
        @Schema(description = "Mã đơn yêu cầu xét duyệt")
        UUID requestId,

        @Schema(description = "Mã người dùng nộp đơn")
        UUID mentorUserId,

        @Schema(
                description = "Trạng thái đơn xét duyệt:<br/>"
                        + "• `DRAFT`: Bản nháp, người dùng đang bổ sung hồ sơ<br/>"
                        + "• `PENDING_REVIEW`: Đã nộp, đang chờ Admin duyệt<br/>"
                        + "• `NEEDS_REVISION`: Admin yêu cầu sửa đổi/bổ sung minh chứng<br/>"
                        + "• `APPROVED`: Đã được duyệt thành công (Đã cấp quyền Mentor)<br/>"
                        + "• `REJECTED`: Đã bị Admin từ chối<br/>"
                        + "• `WITHDRAWN`: Người dùng tự rút đơn",
                example = "DRAFT",
                allowableValues = {"DRAFT", "PENDING_REVIEW", "NEEDS_REVISION", "APPROVED", "REJECTED", "WITHDRAWN"}
        )
        VerificationStatus status,

        @Schema(description = "Ghi chú của người nộp đơn khi submit")
        String submitNote,

        @Schema(description = "Ghi chú đánh giá của Admin")
        String reviewNote,

        @Schema(description = "Lý do từ chối nếu bị reject")
        String rejectionReason,

        @Schema(description = "Số lần đã chỉnh sửa/nộp lại", example = "0")
        Integer revisionCount,

        @Schema(description = "Thời điểm nộp đơn")
        LocalDateTime submittedAt,

        @Schema(description = "Thời gian dự kiến có kết quả duyệt")
        LocalDateTime estimatedReviewBy,

        @Schema(description = "Chỉ tiêu thời gian duyệt (giờ)", example = "24")
        Integer reviewTargetHours,

        @Schema(description = "Đã quá hạn thời gian duyệt dự kiến hay chưa")
        boolean reviewOverdue,

        @Schema(description = "Thời điểm chấp thuận điều khoản nền tảng")
        LocalDateTime termsAcceptedAt,

        @Schema(description = "Phiên bản điều khoản đã chấp thuận")
        String termsVersion,

        @Schema(description = "Thời điểm hoàn tất xét duyệt")
        LocalDateTime reviewedAt,

        @Schema(description = "Thời điểm tạo đơn")
        LocalDateTime createdAt,

        @Schema(description = "Thời điểm cập nhật đơn gần nhất")
        LocalDateTime updatedAt,

        @Schema(description = "Danh sách tài liệu minh chứng đính kèm")
        List<MentorVerificationDocumentResponse> documents,

        @Schema(description = "Lịch sử dòng thời gian xét duyệt")
        List<MentorVerificationTimelineEventResponse> timeline,

        @Schema(description = "Checklist kiểm tra các bước bắt buộc trước khi nộp")
        MentorVerificationChecklistResponse checklist,

        @Schema(description = "Các hành động người dùng được phép thực hiện tiếp theo")
        MentorVerificationAllowedActionsResponse allowedActions
) {
}
