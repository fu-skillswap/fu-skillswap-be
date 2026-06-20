package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Availability rule của mentor")
public record AvailabilityRuleResponse(
        @Schema(description = "ID của rule")
        UUID ruleId,
        @Schema(description = "Loại rule", example = "OPEN")
        AvailabilityRuleType ruleType,
        @Schema(description = "Kiểu lặp của rule", example = "WEEKLY")
        AvailabilityRepeatType repeatType,
        @Schema(description = "Danh sách thứ áp dụng", nullable = true)
        List<DayOfWeek> daysOfWeek,
        @Schema(description = "Ngày bắt đầu hiệu lực")
        LocalDate effectiveFrom,
        @Schema(description = "Ngày kết thúc hiệu lực", nullable = true)
        LocalDate effectiveTo,
        @Schema(description = "Giờ bắt đầu", nullable = true)
        LocalTime startTime,
        @Schema(description = "Giờ kết thúc", nullable = true)
        LocalTime endTime,
        @Schema(description = "Timezone của rule", example = "Asia/Ho_Chi_Minh")
        String timezone,
        @Schema(description = "Rule còn active hay không")
        boolean active,
        @Schema(description = "Ghi chú nội bộ", nullable = true)
        String note,
        @Schema(description = "Thời điểm tạo")
        LocalDateTime createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất")
        LocalDateTime updatedAt
) {
}
