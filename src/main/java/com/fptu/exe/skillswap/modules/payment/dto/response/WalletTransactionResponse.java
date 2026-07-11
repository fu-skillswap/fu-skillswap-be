package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Một giao dịch trong ví Scoin hoặc settlement, chỉ dùng cho hiển thị lịch sử gần nhất.")
public record WalletTransactionResponse(
        UUID id,
        LedgerEntryType entryType,
        CreditOriginType originType,
        LedgerSourceType sourceType,
        UUID sourceId,
        Integer amountScoin,
        Integer balanceEffectScoin,
        String memo,
        LocalDateTime createdAt
) {
}
