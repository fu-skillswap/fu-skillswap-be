package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Payload tạo reschedule request cho booking đã được xác nhận.")
public record CreateBookingRescheduleRequest(
        @NotNull(message = "proposedSlotId là bắt buộc")
        UUID proposedSlotId,
        @NotNull(message = "proposedSelectedStartTime là bắt buộc")
        @Schema(description = "Thời điểm bắt đầu đề xuất, bắt buộc có offset (ví dụ +07:00)", example = "2026-09-01T10:00:00+07:00")
        OffsetDateTime proposedSelectedStartTime,
        @NotNull(message = "proposedSelectedEndTime là bắt buộc")
        @Schema(description = "Thời điểm kết thúc đề xuất, bắt buộc có offset (ví dụ +07:00)", example = "2026-09-01T11:00:00+07:00")
        OffsetDateTime proposedSelectedEndTime,
        @NotBlank(message = "reason không được để trống")
        @Size(max = 1000, message = "reason không được vượt quá 1000 ký tự")
        String reason
) {
}
