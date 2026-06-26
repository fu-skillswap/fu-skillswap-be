package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin một reschedule request của booking.")
public record BookingRescheduleRequestResponse(
        UUID rescheduleRequestId,
        UUID bookingId,
        UUID currentSlotId,
        UUID proposedSlotId,
        LocalDateTime previousSelectedStartTime,
        LocalDateTime previousSelectedEndTime,
        LocalDateTime proposedSelectedStartTime,
        LocalDateTime proposedSelectedEndTime,
        String requesterRole,
        UUID requestedByUserId,
        String responderRole,
        UUID respondedByUserId,
        String status,
        String requestReason,
        String responseNote,
        boolean adminOverride,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt,
        LocalDateTime expiredAt
) {
}
