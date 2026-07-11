package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Ví settlement/mentorship earnings của mentor hiện tại.")
public record MentorWalletResponse(
        Integer availableScoin,
        List<WalletTransactionResponse> recentTransactions
) {
}
