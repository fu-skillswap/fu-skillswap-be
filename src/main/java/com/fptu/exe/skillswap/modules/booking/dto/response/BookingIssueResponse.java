package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Kết quả sau khi participant gửi issue cho booking")
public record BookingIssueResponse(
        UUID bookingId,
        BookingStatus status,
        OffsetDateTime issueSubmittedAt,
        BookingIssueType issueType,
        OffsetDateTime issueRespondedAt
) {
}
