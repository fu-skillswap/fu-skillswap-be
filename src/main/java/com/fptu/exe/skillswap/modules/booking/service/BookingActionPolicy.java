package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMachine;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;

import java.time.Duration;
import java.time.Instant;

/**
 * Single source of truth for role-based booking commands exposed to clients.
 * Persistence states describe what happened; these predicates describe what a
 * participant may do now.
 */
public final class BookingActionPolicy {

    public static final long JOIN_EARLY_MINUTES = 15;
    public static final long JOIN_GRACE_MINUTES = 15;

    private BookingActionPolicy() {
    }

    public static boolean isScheduled(BookingStatus status) {
        return BookingStateMachine.isScheduled(status);
    }

    public static boolean hasSession(BookingStatus status) {
        return isScheduled(status)
                || status == BookingStatus.COMPLETED
                || status == BookingStatus.UNDER_REVIEW;
    }

    public static boolean isTerminal(BookingStatus status) {
        return BookingStateMachine.isTerminal(status);
    }

    public static boolean canCancelByMentee(BookingStatus status, boolean beforeSession) {
        return beforeSession && BookingStateMachine.canTransition(status, BookingTransitionCommand.CANCEL_BY_MENTEE);
    }

    public static boolean canCancelByMentor(BookingStatus status, boolean beforeSession) {
        return beforeSession && BookingStateMachine.canTransition(status, BookingTransitionCommand.CANCEL_BY_MENTOR);
    }

    public static boolean canAcceptOrReject(BookingStatus status, boolean beforePendingDeadline) {
        return beforePendingDeadline && BookingStateMachine.canTransition(status, BookingTransitionCommand.REJECT);
    }

    public static boolean canPay(BookingStatus status, boolean beforePaymentDeadline) {
        return beforePaymentDeadline && BookingStateMachine.canTransition(status, BookingTransitionCommand.PAYMENT_CONFIRMED);
    }

    public static boolean canMentorComplete(BookingStatus status, Instant nowUtc, Instant endUtc) {
        return (status == BookingStatus.PAID || status == BookingStatus.AWAITING_MENTOR_COMPLETION)
                && endUtc != null && nowUtc != null && !nowUtc.isBefore(endUtc);
    }

    public static boolean canMenteeConfirm(BookingStatus status, Instant nowUtc, Instant endUtc) {
        return isOpenPostSessionStatus(status) && isInsideReviewWindow(nowUtc, endUtc);
    }

    public static boolean canReportIssue(BookingStatus status, Instant nowUtc, Instant endUtc) {
        return isOpenPostSessionStatus(status) && isInsideReviewWindow(nowUtc, endUtc);
    }

    public static boolean canJoin(BookingStatus status,
                                  Instant nowUtc,
                                  Instant startUtc,
                                  Instant endUtc,
                                  boolean hasMeetingAccess) {
        if (!isScheduled(status) || !hasMeetingAccess || nowUtc == null || startUtc == null || endUtc == null) {
            return false;
        }
        return !nowUtc.isBefore(startUtc.minus(Duration.ofMinutes(JOIN_EARLY_MINUTES)))
                && nowUtc.isBefore(endUtc.plus(Duration.ofMinutes(JOIN_GRACE_MINUTES)));
    }

    private static boolean isOpenPostSessionStatus(BookingStatus status) {
        // PAID is intentionally included so the UI does not wait for the lifecycle
        // scheduler before exposing confirm/report actions after the session ends.
        return status == BookingStatus.PAID
                || status == BookingStatus.AWAITING_MENTOR_COMPLETION
                || status == BookingStatus.AWAITING_MENTEE_CONFIRMATION;
    }

    private static boolean isInsideReviewWindow(Instant nowUtc, Instant endUtc) {
        return nowUtc != null && endUtc != null
                && !nowUtc.isBefore(endUtc)
                && nowUtc.isBefore(endUtc.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)));
    }
}
