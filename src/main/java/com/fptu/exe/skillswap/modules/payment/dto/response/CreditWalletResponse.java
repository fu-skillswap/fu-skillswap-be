package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Ví Scoin của mentee/user hiện tại.")
public record CreditWalletResponse(
        Integer availableScoin,
        List<WalletTransactionResponse> recentTransactions
) {
}
