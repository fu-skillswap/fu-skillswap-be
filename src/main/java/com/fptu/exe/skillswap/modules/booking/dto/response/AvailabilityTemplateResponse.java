package com.fptu.exe.skillswap.modules.booking.dto.response;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AvailabilitySlotServiceBasicResponse;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplateConfiguredStatus;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplateEffectiveStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AvailabilityTemplateResponse(
        UUID templateId,
        LocalTime startTime,
        LocalTime endTime,
        List<DayOfWeek> weekdays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String timezone,
        String note,
        AvailabilityTemplateConfiguredStatus configuredStatus,
        AvailabilityTemplateEffectiveStatus effectiveStatus,
        Integer configVersion,
        List<AvailabilitySlotServiceBasicResponse> services,
        String generationBlockedReason,
        List<LocalDate> skippedDates,
        List<AvailabilityTemplateBlockedOccurrenceResponse> blockedOccurrences,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
