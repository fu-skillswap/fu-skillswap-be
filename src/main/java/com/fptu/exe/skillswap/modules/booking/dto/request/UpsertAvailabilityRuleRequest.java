package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Deprecated(forRemoval = false)
@Schema(description = "Legacy internal payload cho availability rule. Contract này không còn là public API cho FE mới.")
public record UpsertAvailabilityRuleRequest(
    @Schema(description = "Loại rule: mở lịch hoặc đóng lịch", example = "OPEN")
    @NotNull(message = "ruleType là bắt buộc")
    AvailabilityRuleType ruleType,

        @Schema(description = "Kiểu lặp của rule", example = "WEEKLY")
        @NotNull(message = "repeatType là bắt buộc")
        AvailabilityRepeatType repeatType,

        @Schema(description = "Danh sách thứ áp dụng khi repeatType yêu cầu. Có thể null với rule theo một ngày cụ thể.", nullable = true, example = "[\"MONDAY\",\"WEDNESDAY\"]")
        List<DayOfWeek> daysOfWeek,

        @Schema(description = "Ngày bắt đầu có hiệu lực", example = "2026-06-21")
        @NotNull(message = "effectiveFrom là bắt buộc")
        LocalDate effectiveFrom,

        @Schema(description = "Ngày kết thúc có hiệu lực. Có thể null nếu rule mở cho một ngày hoặc kéo dài theo logic hiện tại.", nullable = true, example = "2026-07-31")
        LocalDate effectiveTo,

        @Schema(description = "Giờ bắt đầu của khung rảnh hoặc khung đóng", nullable = true, example = "19:00")
        LocalTime startTime,

        @Schema(description = "Giờ kết thúc của khung rảnh hoặc khung đóng", nullable = true, example = "21:00")
        LocalTime endTime,

        @Schema(description = "Ghi chú nội bộ của mentor", nullable = true, example = "Rảnh buổi tối sau giờ làm")
        @Size(max = 200, message = "note không được vượt quá 200 ký tự")
        String note
) {
}
