package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Payload tạo reschedule request cho booking đã ACCEPTED.")
public record CreateBookingRescheduleRequest(
        @NotNull(message = "proposedSlotId là bắt buộc")
        UUID proposedSlotId,
        @NotNull(message = "proposedSelectedStartTime là bắt buộc")
        LocalDateTime proposedSelectedStartTime,
        @NotNull(message = "proposedSelectedEndTime là bắt buộc")
        LocalDateTime proposedSelectedEndTime,
        @NotBlank(message = "reason không được để trống")
        @Size(max = 1000, message = "reason không được vượt quá 1000 ký tự")
        String reason
) {
}
