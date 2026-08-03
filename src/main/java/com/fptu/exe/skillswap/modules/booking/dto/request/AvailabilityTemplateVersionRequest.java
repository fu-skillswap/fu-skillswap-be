package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AvailabilityTemplateVersionRequest(
        @NotNull @PositiveOrZero Integer expectedVersion,
        Boolean rejectPendingBookings
) {}
