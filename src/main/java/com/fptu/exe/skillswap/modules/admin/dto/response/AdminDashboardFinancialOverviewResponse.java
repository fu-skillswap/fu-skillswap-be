package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Tổng quan chỉ số tài chính cho admin dashboard.")
public record AdminDashboardFinancialOverviewResponse(
        @Schema(description = "GMV (Gross Merchandise Value) trong 30 ngày qua (Scoin). Tổng giá trị giao dịch payment order PAID.", example = "150000")
        Long gmv30dScoin,
        @Schema(description = "Platform Fee trong 30 ngày qua (Scoin). Tổng doanh thu nền tảng.", example = "15000")
        Long platformFee30dScoin,
        @Schema(description = "Tổng tiền đang bị tạm giữ (Escrow) của Mentor trên toàn hệ thống (VND).", example = "85000000.00")
        BigDecimal totalEscrowVnd,
        @Schema(description = "Tổng số dư khả dụng (Credit Ledger) của toàn bộ user (Scoin).", example = "120000")
        Long totalCreditLedgerScoin
) {
}
