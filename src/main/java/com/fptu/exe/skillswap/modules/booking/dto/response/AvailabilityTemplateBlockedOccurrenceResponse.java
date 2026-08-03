package com.fptu.exe.skillswap.modules.booking.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityTemplateBlockedOccurrenceResponse(LocalDate date, String reason, UUID slotId) {}
