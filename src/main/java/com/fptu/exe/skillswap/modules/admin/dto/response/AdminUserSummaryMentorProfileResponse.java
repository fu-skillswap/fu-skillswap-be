package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Tóm tắt hồ sơ mentor của user cho admin.")
public record AdminUserSummaryMentorProfileResponse(
        @Schema(description = "User có mentor profile hay không", example = "true")
        boolean exists,
        @Schema(description = "Trạng thái mentor hiện tại", example = "ACTIVE")
        String mentorStatus,
        @Schema(description = "Mentor có bật nhận mentoring hay không", example = "true")
        Boolean isAvailable,
        @Schema(description = "Thời điểm verified mentor profile", example = "2026-07-02T10:15:30")
        LocalDateTime verifiedAt,
        @Schema(description = "Headline của mentor", example = "Backend Mentor")
        String headline,
        @Schema(description = "Điểm rating trung bình", example = "4.75")
        BigDecimal averageRating,
        @Schema(description = "Tổng số session đã hoàn thành", example = "15")
        Integer totalCompletedSessions
) {
}
