package com.fptu.exe.skillswap.modules.blog.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record BlogExpectedVersionRequest(@NotNull @PositiveOrZero Integer expectedVersion) {}
