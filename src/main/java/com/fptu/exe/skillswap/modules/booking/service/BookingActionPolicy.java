package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMachine;

import java.time.LocalDateTime;

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
        return beforeSession && (status == BookingStatus.PENDING
                || status == BookingStatus.ACCEPTED_AWAITING_PAYMENT
                || isScheduled(status));
    }

    public static boolean canCancelByMentor(BookingStatus status, boolean beforeSession) {
        return beforeSession && (status == BookingStatus.ACCEPTED_AWAITING_PAYMENT || isScheduled(status));
    }

    public static boolean canAcceptOrReject(BookingStatus status, boolean beforePendingDeadline) {
        return status == BookingStatus.PENDING && beforePendingDeadline;
    }

    public static boolean canPay(BookingStatus status, boolean beforePaymentDeadline) {
        return status == BookingStatus.ACCEPTED_AWAITING_PAYMENT && beforePaymentDeadline;
    }

    public static boolean canMentorComplete(BookingStatus status, LocalDateTime now, LocalDateTime endTime) {
        return (status == BookingStatus.PAID || status == BookingStatus.AWAITING_MENTOR_COMPLETION)
                && endTime != null && !now.isBefore(endTime);
    }

    public static boolean canMenteeConfirm(BookingStatus status, LocalDateTime now, LocalDateTime endTime) {
        return isOpenPostSessionStatus(status) && isInsideReviewWindow(now, endTime);
    }

    public static boolean canReportIssue(BookingStatus status, LocalDateTime now, LocalDateTime endTime) {
        return isOpenPostSessionStatus(status) && isInsideReviewWindow(now, endTime);
    }

    public static boolean canJoin(BookingStatus status,
                                  LocalDateTime now,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  boolean hasMeetingAccess) {
        if (!isScheduled(status) || !hasMeetingAccess || startTime == null || endTime == null) {
            return false;
        }
        return !now.isBefore(startTime.minusMinutes(JOIN_EARLY_MINUTES))
                && now.isBefore(endTime.plusMinutes(JOIN_GRACE_MINUTES));
    }

    private static boolean isOpenPostSessionStatus(BookingStatus status) {
        // PAID is intentionally included so the UI does not wait for the lifecycle
        // scheduler before exposing confirm/report actions after the session ends.
        return status == BookingStatus.PAID
                || status == BookingStatus.AWAITING_MENTOR_COMPLETION
                || status == BookingStatus.AWAITING_MENTEE_CONFIRMATION;
    }

    private static boolean isInsideReviewWindow(LocalDateTime now, LocalDateTime endTime) {
        return endTime != null
                && !now.isBefore(endTime)
                && now.isBefore(endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
    }
}
