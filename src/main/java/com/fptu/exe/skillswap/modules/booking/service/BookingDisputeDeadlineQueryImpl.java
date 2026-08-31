package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTime;
import com.fptu.exe.skillswap.modules.booking.port.BookingDisputeDeadlineQuery;
import com.fptu.exe.skillswap.modules.booking.port.BookingDisputeDeadlineView;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
class BookingDisputeDeadlineQueryImpl implements BookingDisputeDeadlineQuery {
    @Override
    public BookingDisputeDeadlineView resolve(LocalDateTime issueSubmittedAt, LocalDateTime adminEscalatedAt,
                                              LocalDateTime adminSlaOverdueAt, LocalDateTime issueResolvedAt) {
        Instant submitted = BookingTime.toInstant(issueSubmittedAt);
        Instant escalated = BookingTime.toInstant(adminEscalatedAt);
        Instant overdue = BookingTime.toInstant(adminSlaOverdueAt);
        Instant resolved = BookingTime.toInstant(issueResolvedAt);
        return new BookingDisputeDeadlineView(
                BookingTime.fromInstant(BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(submitted)),
                BookingTime.fromInstant(BookingDeadlinePolicy.resolveAdminDisputeSlaDeadlineUtc(escalated)),
                BookingTime.fromInstant(BookingDeadlinePolicy.resolveAdminDisputeAutoReleaseDeadlineUtc(overdue)),
                enumName(BookingDeadlinePolicy.resolveDisputeSlaStatus(submitted, escalated, overdue, resolved)));
    }

    private String enumName(Enum<?> value) { return value == null ? null : value.name(); }
}
