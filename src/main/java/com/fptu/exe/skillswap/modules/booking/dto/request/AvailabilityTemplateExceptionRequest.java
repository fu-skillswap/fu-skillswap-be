package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AvailabilityTemplateExceptionRequest(
        @NotNull @PositiveOrZero Integer expectedVersion,
        Boolean rejectPendingBookings
) {}
