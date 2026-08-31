package com.fptu.exe.skillswap.modules.booking.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Stable, admin-facing dispute projection. Enum values are represented by their wire names. */
public record BookingIssueDetailView(
        UUID bookingId, String status, String issueType, String issueDescription,
        OffsetDateTime issueSubmittedAt, OffsetDateTime issueResponseDeadlineAt, OffsetDateTime issueRespondedAt,
        String issueResponseNote, OffsetDateTime issueResolvedAt, OffsetDateTime issueAdminEscalatedAt,
        OffsetDateTime issueAdminResolutionDeadlineAt, OffsetDateTime issueAdminSlaOverdueAt,
        Integer issueAdminSlaReminderCount, OffsetDateTime issueAutoReleaseAt, String disputeSlaStatus,
        String issueResolutionNote, String issueResolutionAction, String issueResolutionReasonCode,
        Integer issueResolutionMenteeRefundScoin, Integer issueResolutionMentorSettlementScoin,
        Integer issueResolutionPlatformSettlementScoin, List<BookingIssueEvidenceView> evidences
) { }
