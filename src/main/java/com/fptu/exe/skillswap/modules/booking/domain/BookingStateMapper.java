package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.payment.port.PaymentStatusContract;

public final class BookingStateMapper {

    private BookingStateMapper() {
    }

    public static BookingLifecycleStatus toLifecycleStatus(Booking booking) {
        return toLifecycleStatus(booking, null);
    }

    /**
     * Keeps the public lifecycle explicit when the shared internal EXPIRED value
     * came from either a request timeout or a payment timeout.
     */
    public static BookingLifecycleStatus toLifecycleStatus(Booking booking, PaymentStatusContract paymentStatus) {
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
            case PAID, AWAITING_MENTOR_COMPLETION, AWAITING_MENTEE_CONFIRMATION -> BookingLifecycleStatus.CONFIRMED;
            case REJECTED -> BookingLifecycleStatus.REJECTED_BY_MENTOR;
            case EXPIRED -> paymentStatus != null && "EXPIRED".equals(paymentStatus.orderStatus())
                    ? BookingLifecycleStatus.PAYMENT_EXPIRED
                    : BookingLifecycleStatus.REQUEST_EXPIRED;
            case CANCELLED_BY_MENTEE -> BookingLifecycleStatus.CANCELED_BY_MENTEE;
            case CANCELLED_BY_MENTOR -> BookingLifecycleStatus.CANCELED_BY_MENTOR;
            case UNDER_REVIEW -> BookingLifecycleStatus.UNDER_REVIEW;
            case COMPLETED -> BookingLifecycleStatus.COMPLETED;
        };
    }

    public static BookingPaymentStatus toPaymentStatus(Booking booking, PaymentStatusContract paymentStatus) {
        if (booking == null) {
            return null;
        }
        if (Boolean.TRUE.equals(isFreeBooking(booking))) {
            return BookingPaymentStatus.NOT_REQUIRED;
        }
        if (paymentStatus != null && paymentStatus.orderStatus() != null) {
            if ("REFUNDED".equals(paymentStatus.settlementStatus())) {
                return BookingPaymentStatus.REFUNDED;
            }
            return switch (paymentStatus.orderStatus()) {
                case "PENDING", "PARTIALLY_COVERED_BY_CREDIT", "AWAITING_PROVIDER_PAYMENT" -> BookingPaymentStatus.PENDING;
                case "PAID" -> isTerminalCancelled(booking)
                        ? BookingPaymentStatus.REFUNDED
                        : BookingPaymentStatus.PAID;
                case "FAILED" -> BookingPaymentStatus.FAILED;
                case "CANCELLED", "EXPIRED" -> BookingPaymentStatus.EXPIRED;
                default -> fallbackPaymentStatus(booking);
            };
        }
        return switch (booking.getStatus()) {
            case PENDING -> BookingPaymentStatus.PENDING;
            case ACCEPTED_AWAITING_PAYMENT -> BookingPaymentStatus.PENDING;
            case REJECTED, EXPIRED -> BookingPaymentStatus.EXPIRED;
            case CANCELLED_BY_MENTEE, CANCELLED_BY_MENTOR -> BookingPaymentStatus.REFUNDED;
            case AWAITING_MENTOR_COMPLETION, AWAITING_MENTEE_CONFIRMATION, COMPLETED, UNDER_REVIEW, PAID -> BookingPaymentStatus.PAID;
        };
    }

    private static BookingPaymentStatus fallbackPaymentStatus(Booking booking) {
        return switch (booking.getStatus()) {
            case PENDING -> BookingPaymentStatus.PENDING;
            case ACCEPTED_AWAITING_PAYMENT -> BookingPaymentStatus.PENDING;
            case REJECTED, EXPIRED -> BookingPaymentStatus.EXPIRED;
            case CANCELLED_BY_MENTEE, CANCELLED_BY_MENTOR -> BookingPaymentStatus.REFUNDED;
            case AWAITING_MENTOR_COMPLETION, AWAITING_MENTEE_CONFIRMATION, COMPLETED, UNDER_REVIEW, PAID -> BookingPaymentStatus.PAID;
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
                default -> null;
            };
        }
        return switch (outcome) {
            case USER_CONFIRMED -> BookingCompletionOutcome.USER_CONFIRMED;
            case AUTO_CLOSED -> BookingCompletionOutcome.AUTO_CLOSED;
            case UNDER_REVIEW -> BookingCompletionOutcome.UNDER_REVIEW;
            case NO_SHOW_MENTEE -> BookingCompletionOutcome.NO_SHOW_MENTEE;
            case NO_SHOW_MENTOR -> BookingCompletionOutcome.NO_SHOW_MENTOR;
            case PARTIALLY_SETTLED -> BookingCompletionOutcome.PARTIALLY_SETTLED;
            case ADMIN_SLA_AUTO_RELEASED -> BookingCompletionOutcome.ADMIN_SLA_AUTO_RELEASED;
        };
    }

    public static boolean isConfirmedForScheduling(BookingStatus status) {
        return status != null && (status == BookingStatus.PAID
                || status == BookingStatus.AWAITING_MENTOR_COMPLETION
                || status == BookingStatus.AWAITING_MENTEE_CONFIRMATION);
    }

    public static boolean isPaidOrReservedForSchedule(BookingStatus status) {
        return status != null && (status == BookingStatus.ACCEPTED_AWAITING_PAYMENT || isConfirmedForScheduling(status));
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
