package com.fptu.exe.skillswap.modules.chat.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
@Schema(description = "Yêu cầu xóa tin nhắn với kiểm tra phiên bản để tránh xóa nhầm bản đã được cập nhật.")
public record DeleteMessageRequest(
        @Schema(description = "Phiên bản FE đã đọc của tin nhắn. Backend từ chối nếu tin nhắn đã thay đổi.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Integer expectedVersion) {}
