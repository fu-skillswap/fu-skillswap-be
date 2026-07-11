package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Một operator activity item của admin case.")
public record AdminCaseActivityItemResponse(
        @Schema(description = "Loại activity cố định", example = "CASE_ASSIGNMENT")
        String eventType,
        @Schema(description = "Thời điểm event xảy ra", example = "2026-07-02T15:40:00")
        LocalDateTime occurredAt,
        @Schema(description = "Id người thực hiện thao tác", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID actorUserId,
        @Schema(description = "Tên hiển thị của người thực hiện", example = "Admin A")
        String actorDisplayName,
        @Schema(description = "Tiêu đề ngắn của activity", example = "Nhận xử lý case")
        String title,
        @Schema(description = "Mô tả chi tiết activity", example = "Admin A đã nhận xử lý booking này.")
        String description,
        @Schema(description = "Nguồn dữ liệu phát ra activity", example = "AUDIT_LOG")
        String source
) {
}
