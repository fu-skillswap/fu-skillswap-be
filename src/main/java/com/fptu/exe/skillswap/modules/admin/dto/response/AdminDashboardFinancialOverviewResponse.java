package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Tổng quan chỉ số tài chính cho admin dashboard.")
public record AdminDashboardFinancialOverviewResponse(
        @Schema(description = "Chỉ số tài chính trong tháng hiện tại (MTD - Month To Date).")
        FinancialPeriodMetricsResponse thisMonth,
        @Schema(description = "Chỉ số tài chính trong quý hiện tại (QTD - Quarter To Date).")
        FinancialPeriodMetricsResponse thisQuarter,
        @Schema(description = "Chỉ số tài chính trong năm hiện tại (YTD - Year To Date).")
        FinancialPeriodMetricsResponse thisYear,
        @Schema(description = "Tổng tiền đang bị tạm giữ (Escrow) của Mentor trên toàn hệ thống (VND).", example = "85000000.00")
        BigDecimal totalEscrowVnd,
        @Schema(description = "Tổng số dư khả dụng (Credit Ledger) của toàn bộ user (Scoin).", example = "120000")
        Long totalCreditLedgerScoin
) {
}
