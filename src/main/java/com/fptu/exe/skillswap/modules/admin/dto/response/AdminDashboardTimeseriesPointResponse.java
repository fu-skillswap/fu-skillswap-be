package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Một điểm dữ liệu theo ngày của admin dashboard.")
public record AdminDashboardTimeseriesPointResponse(
        @Schema(description = "Ngày theo timezone Asia/Ho_Chi_Minh.", example = "2026-07-02")
        LocalDate date,
        @Schema(description = "Số user được tạo trong ngày.", example = "12")
        long usersCreated,
        @Schema(description = "Số mentor verification được submit trong ngày.", example = "4")
        long mentorVerificationSubmitted,
        @Schema(description = "Số booking được tạo trong ngày.", example = "9")
        long bookingsCreated,
        @Schema(description = "Số payment order chuyển sang PAID trong ngày.", example = "7")
        long paymentOrdersPaid,
        @Schema(description = "Số forum report được tạo trong ngày.", example = "2")
        long forumReportsCreated,
        @Schema(description = "Số payout request được tạo trong ngày.", example = "1")
        long payoutRequestsCreated
) {
}
