package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentSettlementStatus;

import java.util.EnumSet;
import java.util.Set;

public final class BookingStateMapper {

    private static final Set<BookingStatus> FREE_BOOKING_CONFIRMED_STATUSES = EnumSet.of(
            BookingStatus.PAID,
            BookingStatus.ACCEPTED,
            BookingStatus.AWAITING_MENTOR_COMPLETION,
            BookingStatus.AWAITING_MENTEE_CONFIRMATION,
            BookingStatus.COMPLETED,
            BookingStatus.AUTO_CLOSED,
            BookingStatus.UNDER_REVIEW
    );

    private BookingStateMapper() {
    }

    public static BookingLifecycleStatus toLifecycleStatus(Booking booking) {
        if (booking == null) {
            return null;
        }
        BookingStatus status = booking.getStatus();
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> BookingLifecycleStatus.REQUESTED;
            case ACCEPTED_AWAITING_PAYMENT -> BookingLifecycleStatus.WAITING_PAYMENT;
            // ACCEPTED is legacy only. It is shown as confirmed to avoid exposing an ambiguous state.
            case ACCEPTED -> BookingLifecycleStatus.CONFIRMED;
            case PAID, AWAITING_MENTOR_COMPLETION, AWAITING_MENTEE_CONFIRMATION -> BookingLifecycleStatus.CONFIRMED;
            case REJECTED -> BookingLifecycleStatus.REJECTED_BY_MENTOR;
            case EXPIRED -> BookingLifecycleStatus.REQUEST_EXPIRED;
            case CANCELLED_BY_MENTEE -> BookingLifecycleStatus.CANCELED_BY_MENTEE;
            case CANCELLED_BY_MENTOR -> BookingLifecycleStatus.CANCELED_BY_MENTOR;
            case UNDER_REVIEW -> BookingLifecycleStatus.UNDER_REVIEW;
            case COMPLETED, AUTO_CLOSED, NO_SHOW -> BookingLifecycleStatus.COMPLETED;
        };
    }

    public static BookingPaymentStatus toPaymentStatus(Booking booking, PaymentOrder paymentOrder) {
        if (booking == null) {
            return null;
        }
        if (Boolean.TRUE.equals(isFreeBooking(booking))) {
            return BookingPaymentStatus.NOT_REQUIRED;
        }
        if (paymentOrder != null && paymentOrder.getStatus() != null) {
            if (paymentOrder.getSettlementStatus() == PaymentSettlementStatus.REFUNDED) {
                return BookingPaymentStatus.REFUNDED;
            }
            return switch (paymentOrder.getStatus()) {
                case PENDING, PARTIALLY_COVERED_BY_CREDIT, AWAITING_PROVIDER_PAYMENT -> BookingPaymentStatus.PENDING;
                case PAID -> isTerminalCancelled(booking)
                        ? BookingPaymentStatus.REFUNDED
                        : BookingPaymentStatus.PAID;
                case FAILED -> BookingPaymentStatus.FAILED;
                case CANCELLED -> BookingPaymentStatus.EXPIRED;
                case EXPIRED -> BookingPaymentStatus.EXPIRED;
            };
        }
        return switch (booking.getStatus()) {
            case PENDING -> BookingPaymentStatus.PENDING;
            case ACCEPTED_AWAITING_PAYMENT -> BookingPaymentStatus.PENDING;
            case ACCEPTED -> BookingPaymentStatus.PENDING;
            case REJECTED, EXPIRED -> BookingPaymentStatus.EXPIRED;
            case CANCELLED_BY_MENTEE, CANCELLED_BY_MENTOR -> BookingPaymentStatus.REFUNDED;
            case AWAITING_MENTOR_COMPLETION, AWAITING_MENTEE_CONFIRMATION, COMPLETED, AUTO_CLOSED, UNDER_REVIEW, PAID -> BookingPaymentStatus.PAID;
            case NO_SHOW -> BookingPaymentStatus.EXPIRED;
        };
    }

    public static BookingCompletionOutcome toCanonicalCompletionOutcome(Booking booking) {
        if (booking == null) {
            return null;
        }
        BookingCompletionOutcome outcome = booking.getCompletionOutcome();
        if (outcome == null) {
            return switch (booking.getStatus()) {
                case COMPLETED -> BookingCompletionOutcome.USER_CONFIRMED;
                case AUTO_CLOSED -> BookingCompletionOutcome.AUTO_CLOSED;
                default -> null;
            };
        }
        return switch (outcome) {
            case COMPLETED_CONFIRMED, USER_CONFIRMED -> BookingCompletionOutcome.USER_CONFIRMED;
            case COMPLETED_AUTO_CLOSED, AUTO_CLOSED -> BookingCompletionOutcome.AUTO_CLOSED;
            case UNDER_REVIEW, REVIEW_PENDING_DECISION -> BookingCompletionOutcome.UNDER_REVIEW;
            case NO_SHOW_MENTEE -> BookingCompletionOutcome.NO_SHOW_MENTEE;
            case NO_SHOW_MENTOR -> BookingCompletionOutcome.NO_SHOW_MENTOR;
        };
    }

    public static boolean isLegacyConfirmedForScheduling(BookingStatus status) {
        return status != null && (status == BookingStatus.PAID
                || status == BookingStatus.ACCEPTED
                || status == BookingStatus.AWAITING_MENTOR_COMPLETION
                || status == BookingStatus.AWAITING_MENTEE_CONFIRMATION);
    }

    public static boolean isPaidOrReservedForSchedule(BookingStatus status) {
        return status != null && (status == BookingStatus.ACCEPTED_AWAITING_PAYMENT || isLegacyConfirmedForScheduling(status));
    }

    private static boolean isFreeBooking(Booking booking) {
        return booking != null && (Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot())
                || (booking.getServicePriceScoinSnapshot() != null && booking.getServicePriceScoinSnapshot() == 0));
    }

    private static boolean isTerminalCancelled(Booking booking) {
        return booking != null && (booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE
                || booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR
                || booking.getStatus() == BookingStatus.REJECTED
                || booking.getStatus() == BookingStatus.EXPIRED);
    }
}
