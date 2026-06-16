package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record UpsertAvailabilityRuleRequest(
        @NotNull(message = "ruleType là bắt buộc")
        AvailabilityRuleType ruleType,

        @NotNull(message = "repeatType là bắt buộc")
        AvailabilityRepeatType repeatType,

        List<DayOfWeek> daysOfWeek,

        @NotNull(message = "effectiveFrom là bắt buộc")
        LocalDate effectiveFrom,

        LocalDate effectiveTo,

        LocalTime startTime,

        LocalTime endTime,

        @Size(max = 200, message = "note không được vượt quá 200 ký tự")
        String note
) {
}
