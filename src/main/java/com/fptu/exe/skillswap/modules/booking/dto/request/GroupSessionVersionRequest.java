package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record GroupSessionVersionRequest(
        @NotNull @PositiveOrZero Integer expectedVersion
) {}
