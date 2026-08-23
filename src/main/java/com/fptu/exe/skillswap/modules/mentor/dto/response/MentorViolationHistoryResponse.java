package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Tổng điểm vi phạm và lịch sử nội bộ của mentor")
public record MentorViolationHistoryResponse(
        UUID mentorUserId,
        BigDecimal lifetimePenaltyScore,
        BigDecimal activePenaltyScore,
        LocalDateTime activeWindowStartAt,
        LocalDateTime bookingSuspendedUntil,
        long totalViolations,
        PageResponse<MentorViolationItemResponse> history
) {
}
