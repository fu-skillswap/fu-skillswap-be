package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chỉ số tài chính trong một chu kỳ (tháng/quý/năm).")
public record FinancialPeriodMetricsResponse(
        @Schema(description = "Tổng số lượng giao dịch thanh toán thành công (PAID).", example = "150")
        long totalTransactions,
        @Schema(description = "GMV (Gross Merchandise Value) trong chu kỳ (Scoin). Tổng giá trị giao dịch payment order PAID.", example = "150000")
        long gmvScoin,
        @Schema(description = "Platform Fee trong chu kỳ (Scoin). Tổng doanh thu nền tảng.", example = "15000")
        long platformFeeScoin
) {
}
