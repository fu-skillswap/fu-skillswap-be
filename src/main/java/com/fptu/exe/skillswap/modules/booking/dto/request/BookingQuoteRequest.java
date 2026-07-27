package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Read-only quote cho exact availability candidate trước khi tạo booking")
public record BookingQuoteRequest(
        @NotNull UUID slotId,
        @NotNull UUID serviceId,
        @NotNull Instant startAt
) {
}
