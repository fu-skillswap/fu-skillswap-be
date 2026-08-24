package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin một reschedule request của booking.")
public record BookingRescheduleRequestResponse(
        UUID rescheduleRequestId,
        UUID bookingId,
        UUID currentSlotId,
        UUID proposedSlotId,
        @Schema(description = "Thời điểm bắt đầu cũ kèm offset +07:00", example = "2026-09-01T10:00:00+07:00")
        OffsetDateTime previousSelectedStartTime,
        @Schema(description = "Thời điểm kết thúc cũ kèm offset +07:00", example = "2026-09-01T11:00:00+07:00")
        OffsetDateTime previousSelectedEndTime,
        @Schema(description = "Thời điểm bắt đầu đề xuất kèm offset +07:00", example = "2026-09-02T10:00:00+07:00")
        OffsetDateTime proposedSelectedStartTime,
        @Schema(description = "Thời điểm kết thúc đề xuất kèm offset +07:00", example = "2026-09-02T11:00:00+07:00")
        OffsetDateTime proposedSelectedEndTime,
        String requesterRole,
        UUID requestedByUserId,
        String responderRole,
        UUID respondedByUserId,
        String status,
        String requestReason,
        String responseNote,
        boolean adminOverride,
        @Schema(description = "Thời điểm tạo request kèm offset +07:00", example = "2026-08-24T10:00:00+07:00")
        OffsetDateTime requestedAt,
        @Schema(description = "Thời điểm phản hồi kèm offset +07:00", nullable = true)
        OffsetDateTime respondedAt,
        @Schema(description = "Thời điểm hết hạn kèm offset +07:00", nullable = true)
        OffsetDateTime expiredAt
) {
}
