package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateGroupSessionRequest(
        @NotNull UUID sourceSlotId,
        @NotNull @Schema(description = "Whole-minute UTC candidate start", example = "2026-08-10T12:00:00Z") Instant startAt,
        @NotNull @Min(2) @Max(20) Integer maxParticipants,
        @Schema(description = "Optional earlier registration close. Defaults to one hour before start.") Instant registrationClosesAt,
        @Size(max = 1000) String sessionNote
) {}
