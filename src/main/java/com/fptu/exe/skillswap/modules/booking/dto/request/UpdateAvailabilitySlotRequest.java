package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Payload cập nhật slot rảnh cho mentor")
public record UpdateAvailabilitySlotRequest(
        @Schema(description = "Thời gian bắt đầu mới của slot rảnh", example = "2026-06-29T09:00:00")
        @NotNull(message = "startTime là bắt buộc")
        LocalDateTime startTime,

        @Schema(description = "Thời gian kết thúc mới của slot rảnh", example = "2026-06-29T11:00:00")
        @NotNull(message = "endTime là bắt buộc")
        LocalDateTime endTime,

        @Schema(description = "Ghi chú mới cho slot rảnh", example = "Thay đổi giờ rảnh")
        @Size(max = 200, message = "note không được vượt quá 200 ký tự")
        String note,

        @Schema(description = "Danh sách mã môn học/dịch vụ áp dụng mới", example = "[\"019f09d4-8eb0-7952-a820-0808734f7696\"]")
        List<UUID> serviceIds
) {}
