package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Snapshot tổng quan vận hành cho admin dashboard.")
public record AdminDashboardOverviewResponse(
        @Schema(description = "Thời điểm chụp snapshot dashboard.", example = "2026-07-02T14:30:00")
        LocalDateTime snapshotAt,
        @Schema(description = "Khối số liệu tổng quan user.")
        AdminDashboardUsersOverviewResponse users,
        @Schema(description = "Khối số liệu mentor verification.")
        AdminDashboardMentorVerificationOverviewResponse mentorVerification,
        @Schema(description = "Breakdown số booking theo raw booking status. Key giữ nguyên theo enum name.", example = "{\"PENDING\":5,\"PAID\":12}")
        Map<String, Long> bookings,
        @Schema(description = "Breakdown số forum report theo raw forum report status. Key giữ nguyên theo enum name.", example = "{\"OPEN\":3,\"DISMISSED\":1}")
        Map<String, Long> forumReports,
        @Schema(description = "Breakdown số payout request theo raw payout request status. Key giữ nguyên theo enum name.", example = "{\"REQUESTED\":4,\"PAID\":10}")
        Map<String, Long> payoutRequests,
        @Schema(description = "Breakdown số payment order theo raw payment order status. Key giữ nguyên theo enum name.", example = "{\"PENDING\":2,\"FAILED\":1,\"PAID\":9}")
        Map<String, Long> paymentOrders,
        @Schema(description = "Breakdown số email outbox theo raw notification status. Key giữ nguyên theo enum name.", example = "{\"PENDING\":4,\"FAILED\":2,\"SENT\":10}")
        Map<String, Long> emailOutbox
) {
}
