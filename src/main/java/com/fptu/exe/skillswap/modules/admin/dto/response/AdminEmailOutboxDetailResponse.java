package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Chi tiết email outbox dùng cho admin data center.")
public record AdminEmailOutboxDetailResponse(
        @Schema(description = "Id email outbox", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID emailOutboxId,
        @Schema(description = "Email người nhận", example = "user@skillswap.asia")
        String toEmail,
        @Schema(description = "Tiêu đề email", example = "Yeu cau thanh toan booking")
        String subject,
        @Schema(description = "Mã template email", example = "BOOKING_ACCEPTED_EMAIL")
        String templateCode,
        @Schema(description = "Trạng thái email outbox", example = "FAILED")
        String status,
        @Schema(description = "Số lần retry hiện tại", example = "2")
        Integer retryCount,
        @Schema(description = "Thời điểm tạo email outbox", example = "2026-07-02T10:15:30")
        LocalDateTime createdAt,
        @Schema(description = "Thời điểm gửi thành công nếu có", example = "2026-07-02T10:16:30")
        LocalDateTime sentAt,
        @Schema(description = "Bản rút gọn lỗi gần nhất để đọc nhanh trên list", example = "Authentication failed")
        String lastErrorPreview,
        @Schema(description = "Toàn bộ HTML/text body đã lưu trong outbox")
        String body,
        @Schema(description = "Lỗi đầy đủ gần nhất từ pipeline gửi mail")
        String lastError
) {
}
