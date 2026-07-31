package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateGroupSessionRequest(
        @NotNull Instant startAt,
        @NotNull @Min(2) @Max(20) Integer maxParticipants,
        Instant registrationClosesAt,
        @Size(max = 1000) String sessionNote,
        @NotNull @PositiveOrZero Integer expectedVersion
) {}
