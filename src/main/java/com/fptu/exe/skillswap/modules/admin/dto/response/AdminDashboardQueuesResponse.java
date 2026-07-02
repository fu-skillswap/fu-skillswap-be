package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Danh sách queue vận hành mà admin cần xử lý trước.")
public record AdminDashboardQueuesResponse(
        @Schema(description = "Thời điểm chụp snapshot queue.", example = "2026-07-02T14:30:00")
        LocalDateTime snapshotAt,
        @Schema(description = "Danh sách queue card cố định của phase 1.")
        List<AdminDashboardQueueItemResponse> items
) {
}
