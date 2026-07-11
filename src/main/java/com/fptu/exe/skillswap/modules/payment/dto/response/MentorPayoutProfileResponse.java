package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
