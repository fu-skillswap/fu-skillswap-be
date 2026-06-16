package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import lombok.Builder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Builder
public record AvailabilityRuleResponse(
        UUID ruleId,
        AvailabilityRuleType ruleType,
        AvailabilityRepeatType repeatType,
        List<DayOfWeek> daysOfWeek,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalTime startTime,
        LocalTime endTime,
        String timezone,
        boolean active,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
