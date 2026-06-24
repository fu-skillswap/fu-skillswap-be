package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Recurrence pattern used by mentor availability rules.")
public enum AvailabilityRepeatType {
    NONE,
    DAILY,
    WEEKLY
}
