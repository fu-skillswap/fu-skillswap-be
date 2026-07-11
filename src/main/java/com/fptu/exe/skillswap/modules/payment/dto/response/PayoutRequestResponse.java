package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
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
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        LocalDateTime approvedAt,
        LocalDateTime paidAt,
        LocalDateTime rejectedAt
) {
}
