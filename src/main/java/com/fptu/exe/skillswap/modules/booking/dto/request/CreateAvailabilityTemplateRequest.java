package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Weekly mentor availability template. Dates use Asia/Ho_Chi_Minh local calendar semantics.")
public record CreateAvailabilityTemplateRequest(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotEmpty List<@NotNull DayOfWeek> weekdays,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Size(max = 200) String note,
        @NotEmpty List<@NotNull UUID> serviceIds
) {}
