package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateGroupSessionCapacityRequest(
        @NotNull @Min(2) @Max(20) Integer maxParticipants,
        @NotNull @PositiveOrZero Integer expectedVersion
) {}
