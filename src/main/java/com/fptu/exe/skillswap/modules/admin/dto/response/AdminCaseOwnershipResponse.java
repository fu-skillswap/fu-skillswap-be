package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Trạng thái ownership hiện tại của một admin case.")
public record AdminCaseOwnershipResponse(
        @Schema(description = "Loại case", example = "BOOKING")
        String caseType,
        @Schema(description = "Id case", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID caseId,
        @Schema(description = "Case hiện có owner hay chưa", example = "true")
        boolean assigned,
        @Schema(description = "Id admin đang giữ case", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID assignedAdminUserId,
        @Schema(description = "Tên hiển thị của admin đang giữ case", example = "Admin A")
        String assignedAdminDisplayName,
        @Schema(description = "Thời điểm case được assign gần nhất", example = "2026-07-02T15:40:00")
        LocalDateTime assignedAt
) {
}
