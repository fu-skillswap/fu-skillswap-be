package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Một case item trong queue workbench của admin.")
public record AdminQueueCaseItemResponse(
        @Schema(description = "Queue key nguồn của case", example = "booking_under_review")
        String queueKey,
        @Schema(description = "Loại case vận hành", example = "BOOKING")
        String caseType,
        @Schema(description = "Id case", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID caseId,
        @Schema(description = "Tiêu đề hiển thị chính", example = "Need architecture guidance")
        String title,
        @Schema(description = "Dòng mô tả phụ", example = "Mentee A -> Mentor B")
        String subtitle,
        @Schema(description = "Trạng thái raw hiện tại của case", example = "UNDER_REVIEW")
        String status,
        @Schema(description = "Mức độ ưu tiên hiển thị", example = "high")
        String severity,
        @Schema(description = "Thời điểm tạo logic của case", example = "2026-07-02T15:40:00")
        LocalDateTime createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất", example = "2026-07-02T16:10:00")
        LocalDateTime updatedAt,
        @Schema(description = "Số phút đã tồn đọng kể từ createdAt", example = "85")
        long ageMinutes,
        @Schema(description = "Id admin đang giữ case", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID assignedAdminUserId,
        @Schema(description = "Tên admin đang giữ case", example = "Admin A")
        String assignedAdminDisplayName,
        @Schema(description = "Thời điểm assign case", example = "2026-07-02T16:00:00")
        LocalDateTime assignedAt,
        @Schema(description = "API detail path mà FE có thể điều hướng sang", example = "/api/admin/bookings/019f1258-bdb6-7312-ac67-b289909329d1")
        String detailPath,
        @Schema(description = "Danh sách action khả dụng tại queue level", example = "[\"VIEW_DETAIL\",\"ASSIGN_TO_ME\"]")
        List<String> availableActions
) {
}
