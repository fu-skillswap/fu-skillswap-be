package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record ExpectedTemplateVersionRequest(
        @NotNull UUID templateId,
        @NotNull @PositiveOrZero Integer expectedVersion
) {}
