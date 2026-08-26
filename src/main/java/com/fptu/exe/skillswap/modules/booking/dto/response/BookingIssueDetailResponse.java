package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisputeSlaStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Hồ sơ dispute đầy đủ dùng cho mentor, mentee và admin")
public record BookingIssueDetailResponse(
        UUID bookingId,
        BookingStatus status,
        BookingIssueType issueType,
        String issueDescription,
        OffsetDateTime issueSubmittedAt,
        OffsetDateTime issueResponseDeadlineAt,
        OffsetDateTime issueRespondedAt,
        String issueResponseNote,
        OffsetDateTime issueResolvedAt,
        OffsetDateTime issueAdminEscalatedAt,
        OffsetDateTime issueAdminResolutionDeadlineAt,
        OffsetDateTime issueAdminSlaOverdueAt,
        Integer issueAdminSlaReminderCount,
        OffsetDateTime issueAutoReleaseAt,
        BookingDisputeSlaStatus disputeSlaStatus,
        String issueResolutionNote,
        @Schema(description = "Quyết định settlement của admin, nếu dispute đã được resolve", nullable = true)
        AdminBookingIssueResolutionAction issueResolutionAction,
        @Schema(description = "Mã lý do chuẩn của quyết định admin", nullable = true)
        AdminBookingIssueResolutionReasonCode issueResolutionReasonCode,
        @Schema(description = "Số SCoin hoàn cho mentee theo quyết định dispute", nullable = true)
        Integer issueResolutionMenteeRefundScoin,
        @Schema(description = "Số SCoin thanh toán cho mentor theo quyết định dispute", nullable = true)
        Integer issueResolutionMentorSettlementScoin,
        @Schema(description = "Số SCoin nền tảng giữ theo quyết định dispute", nullable = true)
        Integer issueResolutionPlatformSettlementScoin,
        List<BookingIssueEvidenceResponse> evidences
) {
}
