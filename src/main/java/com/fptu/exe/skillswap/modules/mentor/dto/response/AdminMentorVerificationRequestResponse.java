package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.academic.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@Schema(description = "Chi tiết hồ sơ xác thực mentor dành cho admin")
public record AdminMentorVerificationRequestResponse(
        @Schema(description = "ID của hồ sơ")
        UUID requestId,
        @Schema(description = "ID của user mentor")
        UUID mentorUserId,
        @Schema(description = "Email của mentor")
        String mentorEmail,
        @Schema(description = "Họ tên của mentor")
        String mentorFullName,
        @Schema(description = "Ảnh đại diện của mentor")
        String mentorAvatarUrl,
        @Schema(description = "Trạng thái của hồ sơ")
        VerificationStatus status,
        @Schema(description = "Ghi chú của mentor khi nộp hồ sơ")
        String submitNote,
        @Schema(description = "Ghi chú của admin khi duyệt")
        String reviewNote,
        @Schema(description = "Lý do từ chối (nếu có)")
        String rejectionReason,
        @Schema(description = "Số lần đã yêu cầu sửa đổi")
        Integer revisionCount,
        @Schema(description = "Email của admin đã duyệt hồ sơ")
        String reviewerEmail,
        @Schema(description = "Email của admin đang khóa hồ sơ để xử lý")
        String lockedByAdminEmail,
        @Schema(description = "Thời điểm bắt đầu khóa hồ sơ")
        LocalDateTime lockedAt,
        @Schema(description = "Thời điểm hết hạn khóa hồ sơ")
        LocalDateTime lockExpiresAt,
        @Schema(description = "Cờ xác định admin hiện tại có quyền duyệt hồ sơ này hay không")
        boolean canReview,
        @Schema(description = "Thời điểm nộp hồ sơ")
        LocalDateTime submittedAt,
        @Schema(description = "Thời điểm đồng ý điều khoản")
        LocalDateTime termsAcceptedAt,
        @Schema(description = "Phiên bản điều khoản đã đồng ý")
        String termsVersion,
        @Schema(description = "Thời điểm duyệt hồ sơ")
        LocalDateTime reviewedAt,
        @Schema(description = "Thời điểm phê duyệt")
        LocalDateTime approvedAt,
        @Schema(description = "Thời điểm rút hồ sơ")
        LocalDateTime withdrawnAt,
        @Schema(description = "Thời điểm tạo hồ sơ")
        LocalDateTime createdAt,
        @Schema(description = "Thời điểm cập nhật cuối cùng")
        LocalDateTime updatedAt,
        @Schema(description = "Danh sách minh chứng đính kèm")
        List<MentorVerificationDocumentResponse> documents,
        @Schema(description = "Lịch sử duyệt hồ sơ")
        List<MentorVerificationTimelineEventResponse> timeline,
        @Schema(description = "Trạng thái hoàn thành các bước")
        MentorVerificationChecklistResponse checklist,
        @Schema(description = "Thông tin profile mentor")
        MentorProfileResponse mentorProfile,
        @Schema(description = "Thông tin hồ sơ học thuật")
        StudentProfileResponse studentProfile
) {
}
