package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Một giao dịch trong ví Scoin hoặc settlement, chỉ dùng cho hiển thị lịch sử gần nhất.")
public record WalletTransactionResponse(
        @Schema(description = "ID giao dịch ví.") UUID id,
        @Schema(description = "Loại biến động trong ví, dùng để hiển thị lịch sử.") LedgerEntryType entryType,
        @Schema(description = "Nguồn credit nếu có, ví dụ campaign, coupon hoặc refund.") CreditOriginType originType,
        @Schema(description = "Internal field - FE không cần sử dụng nếu chỉ hiển thị lịch sử ví.") LedgerSourceType sourceType,
        @Schema(description = "Internal field - FE không cần sử dụng nếu chỉ hiển thị lịch sử ví.") UUID sourceId,
        @Schema(description = "Số Scoin của giao dịch.") Integer amountScoin,
        @Schema(description = "Mức thay đổi số dư sau giao dịch.") Integer balanceEffectScoin,
        @Schema(description = "Nội dung giải thích giao dịch.", nullable = true) String memo,
        @Schema(description = "Thời điểm tạo giao dịch kèm offset +07:00", example = "2026-08-24T19:00:00+07:00")
        OffsetDateTime createdAt
) {
}
