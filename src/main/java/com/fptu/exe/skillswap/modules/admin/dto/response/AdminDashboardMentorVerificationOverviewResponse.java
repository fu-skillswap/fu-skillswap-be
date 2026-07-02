package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Breakdown trạng thái mentor verification cho admin dashboard.")
public record AdminDashboardMentorVerificationOverviewResponse(
        @Schema(description = "Số request đang ở trạng thái DRAFT.", example = "14")
        long draft,
        @Schema(description = "Số request đang chờ admin review.", example = "6")
        long pendingReview,
        @Schema(description = "Số request đang cần mentor chỉnh sửa.", example = "3")
        long needsRevision,
        @Schema(description = "Số request đã được approved.", example = "180")
        long approved,
        @Schema(description = "Số request đã bị reject.", example = "7")
        long rejected,
        @Schema(description = "Số request đã bị mentor withdraw.", example = "2")
        long withdrawn
) {
}
