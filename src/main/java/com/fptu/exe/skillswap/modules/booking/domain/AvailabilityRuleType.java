package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Availability rule type that decides whether a rule opens time for slot generation or blocks time from being offered.")
public enum AvailabilityRuleType {
    OPEN,
    CLOSED
}
