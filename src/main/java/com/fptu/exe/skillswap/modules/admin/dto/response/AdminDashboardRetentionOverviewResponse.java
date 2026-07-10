package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tổng quan chỉ số retention và conversion cho admin dashboard.")
public record AdminDashboardRetentionOverviewResponse(
        @Schema(description = "Tỷ lệ chuyển đổi từ Sign Up -> Mentor Verification (%).", example = "15.5")
        Double signupToMentorConversionRate,
        @Schema(description = "DAU (Daily Active Users) trong 24h qua.", example = "120")
        Long dau,
        @Schema(description = "MAU (Monthly Active Users) trong 30 ngày qua.", example = "1500")
        Long mau
) {
}
