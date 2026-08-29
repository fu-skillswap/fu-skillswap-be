package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fptu.exe.skillswap.shared.time.FlexibleInstantDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Read-only quote cho exact availability candidate trước khi tạo booking")
public record BookingQuoteRequest(
        @NotNull UUID slotId,
        @NotNull UUID serviceId,
        @NotNull @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant startAt
) {
}
