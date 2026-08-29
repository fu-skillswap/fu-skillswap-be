package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisputeSlaStatus;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * @deprecated Moved to {@link com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy}.
 */
@Deprecated(forRemoval = true)
public final class BookingDeadlinePolicy {

    public static final long PENDING_RESPONSE_WINDOW_HOURS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.PENDING_RESPONSE_WINDOW_HOURS;
    public static final long PENDING_RESPONSE_PREPARATION_HOURS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.PENDING_RESPONSE_PREPARATION_HOURS;
    public static final long PAYMENT_WINDOW_MINUTES = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.PAYMENT_WINDOW_MINUTES;
    public static final long PAYMENT_PREPARATION_MINUTES = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.PAYMENT_PREPARATION_MINUTES;
    public static final long CANCELLATION_EARLY_WINDOW_MINUTES = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.CANCELLATION_EARLY_WINDOW_MINUTES;
    public static final long ISSUE_RESPONSE_WINDOW_HOURS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.ISSUE_RESPONSE_WINDOW_HOURS;
    public static final long ISSUE_RESPONSE_REMINDER_HOURS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.ISSUE_RESPONSE_REMINDER_HOURS;
    public static final long ADMIN_DISPUTE_RESOLUTION_WINDOW_HOURS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.ADMIN_DISPUTE_RESOLUTION_WINDOW_HOURS;
    public static final long ADMIN_DISPUTE_OVERDUE_REMINDER_INTERVAL_HOURS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.ADMIN_DISPUTE_OVERDUE_REMINDER_INTERVAL_HOURS;
    public static final int MAX_ADMIN_DISPUTE_OVERDUE_REMINDERS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.MAX_ADMIN_DISPUTE_OVERDUE_REMINDERS;
    public static final long ADMIN_DISPUTE_FINAL_ACTION_GRACE_HOURS = com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.ADMIN_DISPUTE_FINAL_ACTION_GRACE_HOURS;

    private BookingDeadlinePolicy() {
    }

    public static String paymentDeadlineText() {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.paymentDeadlineText();
    }

    public static boolean isLateCancellation(long minutesUntilStart) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.isLateCancellation(minutesUntilStart);
    }

    public static Instant resolvePendingExpiry(Instant createdAtUtc, Instant selectedStartAtUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolvePendingExpiry(createdAtUtc, selectedStartAtUtc);
    }

    public static LocalDateTime resolvePendingExpiry(LocalDateTime createdAt, LocalDateTime selectedStartAt) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolvePendingExpiry(createdAt, selectedStartAt);
    }

    public static Instant resolvePaymentDeadline(Instant acceptedAtUtc, Instant selectedStartAtUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAtUtc, selectedStartAtUtc);
    }

    public static LocalDateTime resolvePaymentDeadline(LocalDateTime acceptedAt, LocalDateTime selectedStartAt) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAt, selectedStartAt);
    }

    public static Instant resolvePaymentDeadlineUtc(Booking booking) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolvePaymentDeadlineUtc(booking);
    }

    public static boolean isPaymentDeadlineReachedUtc(Booking booking, Instant nowUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.isPaymentDeadlineReachedUtc(booking, nowUtc);
    }

    public static LocalDateTime resolvePaymentDeadline(Booking booking) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolvePaymentDeadline(booking);
    }

    public static Instant resolveReviewDeadlineUtc(Instant sessionEndUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolveReviewDeadlineUtc(sessionEndUtc);
    }

    public static Instant resolveAutoCloseWarningDeadlineUtc(Instant sessionEndUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolveAutoCloseWarningDeadlineUtc(sessionEndUtc);
    }

    public static Instant resolveIssueResponseDeadlineUtc(Instant issueSubmittedUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(issueSubmittedUtc);
    }

    public static Instant resolveIssueEscalationDeadlineUtc(Instant issueSubmittedUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolveIssueEscalationDeadlineUtc(issueSubmittedUtc);
    }

    public static Instant resolveAdminDisputeSlaDeadlineUtc(Instant adminEscalatedUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolveAdminDisputeSlaDeadlineUtc(adminEscalatedUtc);
    }

    public static Instant resolveAdminDisputeAutoReleaseDeadlineUtc(Instant adminSlaOverdueUtc) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolveAdminDisputeAutoReleaseDeadlineUtc(adminSlaOverdueUtc);
    }

    public static BookingDisputeSlaStatus resolveDisputeSlaStatus(
            Instant issueSubmittedUtc,
            Instant adminEscalatedUtc,
            Instant adminSlaOverdueUtc,
            Instant issueResolvedUtc
    ) {
        return com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolveDisputeSlaStatus(
                issueSubmittedUtc, adminEscalatedUtc, adminSlaOverdueUtc, issueResolvedUtc);
    }
}
