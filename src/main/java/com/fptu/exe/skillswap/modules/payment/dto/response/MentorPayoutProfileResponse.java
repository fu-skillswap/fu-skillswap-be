package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin payout profile của mentor")
public record MentorPayoutProfileResponse(
        UUID payoutProfileId,
        UUID mentorUserId,
        String accountHolderName,
        String bankCode,
        String bankName,
        String accountNumberMasked,
        Boolean isDefault,
        Boolean isActive,
        @Schema(description = "Thời điểm tạo profile kèm offset +07:00", example = "2026-08-24T19:00:00+07:00")
        OffsetDateTime createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất kèm offset +07:00", example = "2026-08-24T19:30:00+07:00")
        OffsetDateTime updatedAt
) {
}
