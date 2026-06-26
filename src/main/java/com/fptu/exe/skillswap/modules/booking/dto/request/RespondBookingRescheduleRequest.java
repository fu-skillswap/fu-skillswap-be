package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload phản hồi reschedule request.")
public record RespondBookingRescheduleRequest(
        @NotBlank(message = "reason không được để trống")
        @Size(max = 1000, message = "reason không được vượt quá 1000 ký tự")
        String reason
) {
}
