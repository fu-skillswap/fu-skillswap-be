package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload hủy booking từ mentee hoặc mentor")
public record CancelBookingRequest(
        @Schema(description = "Lý do hủy booking để hiển thị cho phía còn lại và phục vụ audit", example = "Em có lịch thi đột xuất nên xin hủy và đặt lại slot khác.")
        @NotBlank(message = "cancelReason không được để trống")
        @Size(max = 1000, message = "cancelReason không được vượt quá 1000 ký tự")
        String cancelReason
) {
}
