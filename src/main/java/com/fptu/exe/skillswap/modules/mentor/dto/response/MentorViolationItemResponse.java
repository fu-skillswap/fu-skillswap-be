package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSeverity;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Một lần mentor vi phạm quy tắc vận hành; chỉ mentor đó và admin nhìn thấy")
public record MentorViolationItemResponse(
        UUID violationId,
        MentorViolationType type,
        MentorViolationSource sourceModule,
        MentorViolationSeverity severity,
        BigDecimal points,
        String reason,
        UUID bookingId,
        LocalDateTime occurredAt,
        LocalDateTime reversedAt
) {
}
