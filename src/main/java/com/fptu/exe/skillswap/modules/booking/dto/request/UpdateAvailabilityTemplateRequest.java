package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record UpdateAvailabilityTemplateRequest(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotEmpty List<@NotNull DayOfWeek> weekdays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Size(max = 200) String note,
        @NotEmpty List<@NotNull UUID> serviceIds,
        @NotNull @PositiveOrZero Integer expectedVersion,
        Boolean rejectPendingBookings
) {}
