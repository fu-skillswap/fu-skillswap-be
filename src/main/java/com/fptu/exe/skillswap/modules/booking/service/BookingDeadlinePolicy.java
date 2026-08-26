package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/** Shared, server-enforced deadlines for booking response and payment hold windows. */
public final class BookingDeadlinePolicy {

    public static final long PENDING_RESPONSE_WINDOW_HOURS = 12;
    public static final long PENDING_RESPONSE_PREPARATION_HOURS = 3;
    /** Async mentor approval still gives the mentee time to react without holding a scarce slot for hours. */
    public static final long PAYMENT_WINDOW_MINUTES = 60;
    public static final long PAYMENT_PREPARATION_MINUTES = 60;
    /** Dispute SLA is measured from the server timestamp at issue submission/escalation. */
    public static final long ISSUE_RESPONSE_WINDOW_HOURS = 24;
    public static final long ISSUE_RESPONSE_REMINDER_HOURS = 12;
    public static final long ADMIN_DISPUTE_RESOLUTION_WINDOW_HOURS = 48;
    public static final long ADMIN_DISPUTE_OVERDUE_REMINDER_INTERVAL_HOURS = 24;
    public static final int MAX_ADMIN_DISPUTE_OVERDUE_REMINDERS = 3;
    public static final long ADMIN_DISPUTE_FINAL_ACTION_GRACE_HOURS = 24;

    private BookingDeadlinePolicy() {
    }

    public static Instant resolvePendingExpiry(Instant createdAtUtc, Instant selectedStartAtUtc) {
        if (createdAtUtc == null || selectedStartAtUtc == null) {
            return null;
        }
        Instant responseDeadline = createdAtUtc.plus(Duration.ofHours(PENDING_RESPONSE_WINDOW_HOURS));
        Instant preparationDeadline = selectedStartAtUtc.minus(Duration.ofHours(PENDING_RESPONSE_PREPARATION_HOURS));
        return responseDeadline.isBefore(preparationDeadline) ? responseDeadline : preparationDeadline;
    }

    public static LocalDateTime resolvePendingExpiry(LocalDateTime createdAt, LocalDateTime selectedStartAt) {
        if (createdAt == null || selectedStartAt == null) {
            return null;
        }
        LocalDateTime responseDeadline = createdAt.plusHours(PENDING_RESPONSE_WINDOW_HOURS);
        LocalDateTime preparationDeadline = selectedStartAt.minusHours(PENDING_RESPONSE_PREPARATION_HOURS);
        return responseDeadline.isBefore(preparationDeadline) ? responseDeadline : preparationDeadline;
    }

    public static Instant resolvePaymentDeadline(Instant acceptedAtUtc, Instant selectedStartAtUtc) {
        if (acceptedAtUtc == null) {
            return selectedStartAtUtc == null ? null : selectedStartAtUtc.minus(Duration.ofMinutes(PAYMENT_PREPARATION_MINUTES));
        }
        Instant paymentWindowDeadline = acceptedAtUtc.plus(Duration.ofMinutes(PAYMENT_WINDOW_MINUTES));
        if (selectedStartAtUtc == null) {
            return paymentWindowDeadline;
        }
        Instant preparationDeadline = selectedStartAtUtc.minus(Duration.ofMinutes(PAYMENT_PREPARATION_MINUTES));
        return paymentWindowDeadline.isBefore(preparationDeadline) ? paymentWindowDeadline : preparationDeadline;
    }

    public static LocalDateTime resolvePaymentDeadline(LocalDateTime acceptedAt, LocalDateTime selectedStartAt) {
        if (acceptedAt == null) {
            return selectedStartAt == null ? null : selectedStartAt.minusMinutes(PAYMENT_PREPARATION_MINUTES);
        }
        LocalDateTime paymentWindowDeadline = acceptedAt.plusMinutes(PAYMENT_WINDOW_MINUTES);
        if (selectedStartAt == null) {
            return paymentWindowDeadline;
        }
        LocalDateTime preparationDeadline = selectedStartAt.minusMinutes(PAYMENT_PREPARATION_MINUTES);
        return paymentWindowDeadline.isBefore(preparationDeadline) ? paymentWindowDeadline : preparationDeadline;
    }

    public static Instant resolvePaymentDeadlineUtc(Booking booking) {
        if (booking == null) {
            return null;
        }
        Instant startUtc = booking.getSelectedStartTimeUtc() != null ? booking.getSelectedStartTimeUtc()
                : (booking.getSlot() != null && booking.getSlot().getStartTimeUtc() != null ? booking.getSlot().getStartTimeUtc()
                : (booking.getSelectedStartTime() != null ? BookingTime.toInstant(booking.getSelectedStartTime())
                : (booking.getSlot() != null ? BookingTime.toInstant(booking.getSlot().getStartTime()) : null)));
        Instant acceptedAtUtc = booking.getAcceptedAtUtc() != null ? booking.getAcceptedAtUtc()
                : (booking.getAcceptedAt() != null ? BookingTime.toInstant(booking.getAcceptedAt()) : null);
        return resolvePaymentDeadline(acceptedAtUtc, startUtc);
    }

    public static boolean isPaymentDeadlineReachedUtc(Booking booking, Instant nowUtc) {
        Instant deadline = resolvePaymentDeadlineUtc(booking);
        return deadline != null && nowUtc != null && !deadline.isAfter(nowUtc);
    }

    public static LocalDateTime resolvePaymentDeadline(Booking booking) {
        if (booking == null) {
            return null;
        }
        LocalDateTime start = booking.getSelectedStartTime() != null ? booking.getSelectedStartTime()
                : (booking.getSlot() != null ? booking.getSlot().getStartTime() : null);
        return resolvePaymentDeadline(booking.getAcceptedAt(), start);
    }

    public static Instant resolveReviewDeadlineUtc(Instant sessionEndUtc) {
        return sessionEndUtc != null ? sessionEndUtc.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)) : null;
    }

    public static Instant resolveAutoCloseWarningDeadlineUtc(Instant sessionEndUtc) {
        return sessionEndUtc != null ? sessionEndUtc.plus(Duration.ofHours(PostSessionPolicy.AUTO_CLOSE_WARNING_HOURS)) : null;
    }

    public static Instant resolveIssueResponseDeadlineUtc(Instant issueSubmittedUtc) {
        return issueSubmittedUtc != null ? issueSubmittedUtc.plus(Duration.ofHours(ISSUE_RESPONSE_WINDOW_HOURS)) : null;
    }

    public static Instant resolveIssueEscalationDeadlineUtc(Instant issueSubmittedUtc) {
        return issueSubmittedUtc != null ? issueSubmittedUtc.plus(Duration.ofHours(ISSUE_RESPONSE_REMINDER_HOURS)) : null;
    }

    /** The admin SLA starts only after the case has actually entered the admin queue. */
    public static Instant resolveAdminDisputeSlaDeadlineUtc(Instant adminEscalatedUtc) {
        return adminEscalatedUtc != null ? adminEscalatedUtc.plus(Duration.ofHours(ADMIN_DISPUTE_RESOLUTION_WINDOW_HOURS)) : null;
    }

    public static Instant resolveAdminDisputeAutoReleaseDeadlineUtc(Instant adminSlaOverdueUtc) {
        if (adminSlaOverdueUtc == null) return null;
        // Reminder #1 is emitted at the overdue moment itself; only the remaining
        // reminders consume another full interval before the final grace period.
        long hoursUntilFinalAction = (long) ADMIN_DISPUTE_OVERDUE_REMINDER_INTERVAL_HOURS
                * Math.max(0, MAX_ADMIN_DISPUTE_OVERDUE_REMINDERS - 1) + ADMIN_DISPUTE_FINAL_ACTION_GRACE_HOURS;
        return adminSlaOverdueUtc.plus(Duration.ofHours(hoursUntilFinalAction));
    }
}
