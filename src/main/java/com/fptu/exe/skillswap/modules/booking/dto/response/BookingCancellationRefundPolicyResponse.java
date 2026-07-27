package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Platform cancellation and refund policy currently applied to bookings")
public record BookingCancellationRefundPolicyResponse(
        int earlyMenteeCancellationDeadlineMinutes,
        int earlyMenteeRefundPercent,
        int lateMenteeRefundPercent,
        int mentorCancellationRefundPercent,
        int mentorNoShowRefundPercent
) {
}
