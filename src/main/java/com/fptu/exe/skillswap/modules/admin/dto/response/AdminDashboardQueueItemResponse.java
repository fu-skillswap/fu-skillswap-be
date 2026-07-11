package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một queue card cần admin chú ý trên dashboard.")
public record AdminDashboardQueueItemResponse(
        @Schema(description = "Khóa logic ổn định cho queue item.", example = "mentor_verification_pending_review")
        String key,
        @Schema(description = "Nhãn hiển thị ngắn gọn cho FE.", example = "Mentor verification chờ duyệt")
        String label,
        @Schema(description = "Số lượng item đang tồn đọng trong queue này.", example = "6")
        long count,
        @Schema(description = "Mức độ ưu tiên hiển thị cho FE.", example = "high")
        String severity,
        @Schema(description = "API family hoặc route gợi ý để FE điều hướng khi admin bấm vào queue này.", example = "/api/admin/mentor-verification/requests?status=PENDING_REVIEW")
        String targetPath,
        @Schema(description = "Thứ tự ưu tiên cố định của queue trên dashboard.", example = "1")
        int priorityOrder
) {
}
