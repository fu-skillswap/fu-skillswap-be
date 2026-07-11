package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tóm tắt hoạt động của user cho admin.")
public record AdminUserSummaryActivityResponse(
        @Schema(description = "Số booking user tạo ở vai mentee", example = "6")
        long menteeBookingCount,
        @Schema(description = "Số booking user nhận ở vai mentor", example = "8")
        long mentorBookingCount,
        @Schema(description = "Số payment orders user đã tạo", example = "4")
        long paymentOrderCount,
        @Schema(description = "Số payout request user đã tạo", example = "2")
        long payoutRequestCount,
        @Schema(description = "Số forum report user đã gửi", example = "1")
        long forumReportCreatedCount
) {
}
