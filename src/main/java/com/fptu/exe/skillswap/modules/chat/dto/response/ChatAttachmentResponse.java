package com.fptu.exe.skillswap.modules.chat.dto.response;

import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachmentState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ChatAttachmentResponse(
        @Schema(description = "ID file đính kèm.", example = "019f8234-aaaa-bbbb-cccc-1234567890ab")
        UUID attachmentId,
        @Schema(description = "Tên file để hiển thị.", example = "lesson-notes.pdf")
        String filename,
        @Schema(description = "MIME type của file.", example = "application/pdf")
        String contentType,
        @Schema(description = "Kích thước file tính bằng byte.", example = "245760")
        long sizeBytes,
        @Schema(description = "FE có thể hiển thị nội dung ngay trong màn hình hay không.", example = "false")
        boolean inlineCapable,
        @Schema(description = "FE có thể cho người dùng tải file xuống hay không.", example = "true")
        boolean downloadable,
        @Schema(description = "Thời điểm URL truy cập file hết hạn theo UTC. Sau thời điểm này FE cần xin URL mới.", example = "2026-06-25T04:45:00Z", nullable = true)
        Instant expiresAt,
        @Schema(description = "Trạng thái file đính kèm để FE biết có thể hiển thị hay cần chờ xử lý.", example = "READY")
        ChatAttachmentState state
) {
}
