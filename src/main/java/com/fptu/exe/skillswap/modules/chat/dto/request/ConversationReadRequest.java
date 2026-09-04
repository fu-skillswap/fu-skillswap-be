package com.fptu.exe.skillswap.modules.chat.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
@Schema(description = "Cập nhật vị trí đọc của người dùng hiện tại trong cuộc trò chuyện. FE gửi sequence lớn nhất đã hiển thị.")
public record ConversationReadRequest(
        @Schema(description = "Sequence lớn nhất FE đã đọc; không giảm giá trị đã đồng bộ trước đó.", example = "128", requiredMode = Schema.RequiredMode.REQUIRED)
        @PositiveOrZero long lastReadSequence) {}
