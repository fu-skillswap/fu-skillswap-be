package com.fptu.exe.skillswap.modules.mentor.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MentorAvailabilitySlotResponse(
        UUID slotId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String timezone,
        Integer durationMinutes,
        TeachingMode teachingMode,
        boolean recurring
) {
}
