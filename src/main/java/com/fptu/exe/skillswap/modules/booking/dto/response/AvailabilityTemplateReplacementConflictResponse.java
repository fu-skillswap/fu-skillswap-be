package com.fptu.exe.skillswap.modules.booking.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/** Metadata required to retry an explicit generated-occurrence replacement safely. */
public record AvailabilityTemplateReplacementConflictResponse(
        UUID templateId,
        LocalDate occurrenceDate,
        Integer currentVersion
) {}
