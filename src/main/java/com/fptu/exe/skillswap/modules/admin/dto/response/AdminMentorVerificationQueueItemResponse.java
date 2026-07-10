package com.fptu.exe.skillswap.modules.admin.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@Schema(description = "Thông tin tóm tắt của hồ sơ xác thực mentor trong hàng chờ admin")
public record AdminMentorVerificationQueueItemResponse(
        @Schema(description = "ID của hồ sơ xác thực")
        UUID requestId,
        @Schema(description = "ID của user mentor")
        UUID mentorUserId,
        @Schema(description = "Email của mentor")
        String mentorEmail,
        @Schema(description = "Họ tên của mentor")
        String mentorFullName,
        @Schema(description = "Ảnh đại diện của mentor")
        String mentorAvatarUrl,
        @Schema(description = "Trạng thái của hồ sơ (PENDING, APPROVED, REJECTED, REVISION_REQUESTED)")
        VerificationStatus status,
        @Schema(description = "Số lần đã yêu cầu sửa đổi")
        Integer revisionCount,
        @Schema(description = "Thời điểm mentor nộp hồ sơ gần nhất")
        LocalDateTime submittedAt,
        @Schema(description = "Thời điểm tạo hồ sơ")
        LocalDateTime createdAt,
        @Schema(description = "Thời điểm cập nhật hồ sơ cuối cùng")
        LocalDateTime updatedAt
) {
}
