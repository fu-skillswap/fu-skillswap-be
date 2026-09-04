package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Internal/System - không dùng cho FE người dùng. Một dòng audit log nội bộ dành cho admin.")
public record AdminAuditLogItemResponse(
        @Schema(description = "Id audit log", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID auditLogId,
        @Schema(description = "Thời điểm phát sinh audit log", example = "2026-07-02T10:15:30")
        LocalDateTime createdAt,
        @Schema(description = "Id user thực hiện hành động", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID actorUserId,
        @Schema(description = "Tên hiển thị của actor", example = "Vo Quang Tam")
        String actorDisplayName,
        @Schema(description = "Loại entity bị tác động", example = "USER")
        String entityType,
        @Schema(description = "Id entity bị tác động", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID entityId,
        @Schema(description = "Loại action", example = "UPDATE")
        String action,
        @Schema(description = "Internal/System - snapshot cũ dạng raw JSON/string.", example = "{\"status\":\"ACTIVE\"}")
        String oldValue,
        @Schema(description = "Internal/System - snapshot mới dạng raw JSON/string.", example = "{\"status\":\"BANNED\"}")
        String newValue,
        @Schema(description = "Internal/System - IP address được lưu trong audit log.", example = "127.0.0.1")
        String ipAddress,
        @Schema(description = "Internal/System - User-Agent được lưu trong audit log.", example = "Mozilla/5.0")
        String userAgent
) {
}
