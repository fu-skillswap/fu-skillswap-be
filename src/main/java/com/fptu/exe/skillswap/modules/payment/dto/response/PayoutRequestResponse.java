package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin payout request của mentor")
public record PayoutRequestResponse(
        UUID payoutRequestId,
        UUID mentorUserId,
        UUID settlementAccountId,
        UUID payoutProfileId,
        Integer amountScoin,
        PayoutRequestStatus status,
        String bankAccountNameSnapshot,
        String bankNameSnapshot,
        String bankAccountNumberMaskedSnapshot,
        UUID adminUserId,
        String adminNote,
        @Schema(description = "Thời điểm tạo payout request kèm offset +07:00", example = "2026-08-24T19:00:00+07:00")
        OffsetDateTime requestedAt,
        @Schema(description = "Thời điểm bắt đầu review kèm offset +07:00", nullable = true)
        OffsetDateTime reviewedAt,
        @Schema(description = "Thời điểm approve kèm offset +07:00", nullable = true)
        OffsetDateTime approvedAt,
        @Schema(description = "Thời điểm đã thanh toán kèm offset +07:00", nullable = true)
        OffsetDateTime paidAt,
        @Schema(description = "Thời điểm từ chối kèm offset +07:00", nullable = true)
        OffsetDateTime rejectedAt
) {
}
