package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Admin note nội bộ gắn với một target vận hành.")
public record AdminNoteResponse(
        @Schema(description = "Id của admin note", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID noteId,
        @Schema(description = "Loại target được ghi chú", example = "BOOKING")
        String targetType,
        @Schema(description = "Id target được ghi chú", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID targetId,
        @Schema(description = "Nội dung ghi chú nội bộ", example = "Đã liên hệ để xác minh thêm.")
        String note,
        @Schema(description = "Id admin đã tạo note", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID adminUserId,
        @Schema(description = "Tên hiển thị của admin đã tạo note", example = "Vo Quang Tam")
        String adminDisplayName,
        @Schema(description = "Thời điểm tạo note", example = "2026-07-02T10:15:30")
        LocalDateTime createdAt
) {
}
