package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload đánh dấu booking đã hoàn thành")
public record CompleteBookingRequest(
        @Schema(description = "Ghi chú ngắn sau buổi mentoring, dùng cho lịch sử nội bộ", nullable = true, example = "Đã review CV và thống nhất lộ trình học 4 tuần.")
        @Size(max = 2000, message = "completionNote không được vượt quá 2000 ký tự")
        String completionNote
) {
}
