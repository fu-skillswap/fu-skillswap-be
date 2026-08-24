package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

/** Pure, side-effect-free source of truth for persisted booking status transitions. */
public final class BookingStateMachine {

    private BookingStateMachine() {
    }

    public static BookingStatus target(BookingStatus current, BookingTransitionCommand command) {
        if (current == null || command == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu trạng thái hoặc lệnh chuyển trạng thái booking");
        }
        BookingStatus target = switch (command) {
            case ACCEPT_FREE -> require(current, BookingStatus.PENDING, BookingStatus.PAID, command);
            case ACCEPT_PAID -> require(current, BookingStatus.PENDING, BookingStatus.ACCEPTED_AWAITING_PAYMENT, command);
            case REJECT, SYSTEM_REJECT -> require(current, BookingStatus.PENDING, BookingStatus.REJECTED, command);
            case EXPIRE_PENDING -> require(current, BookingStatus.PENDING, BookingStatus.EXPIRED, command);
            case EXPIRE_PAYMENT -> require(current, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.EXPIRED, command);
            case CANCEL_BY_MENTEE -> requireAny(current,
                    new BookingStatus[]{BookingStatus.PENDING, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.PAID},
                    BookingStatus.CANCELLED_BY_MENTEE, command);
            case CANCEL_BY_MENTOR -> requireAny(current,
                    new BookingStatus[]{BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.PAID},
                    BookingStatus.CANCELLED_BY_MENTOR, command);
            case PAYMENT_CONFIRMED -> require(current, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.PAID, command);
            case SESSION_ENDED -> require(current, BookingStatus.PAID, BookingStatus.AWAITING_MENTOR_COMPLETION, command);
            case MENTOR_COMPLETED -> require(current, BookingStatus.AWAITING_MENTOR_COMPLETION,
                    BookingStatus.AWAITING_MENTEE_CONFIRMATION, command);
            case MENTEE_CONFIRMED -> requireAny(current,
                    new BookingStatus[]{BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION},
                    BookingStatus.COMPLETED, command);
            case ISSUE_REPORTED -> requireAny(current,
                    new BookingStatus[]{BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION},
                    BookingStatus.UNDER_REVIEW, command);
            case AUTO_CLOSE -> requireAny(current,
                    new BookingStatus[]{BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION},
                    BookingStatus.COMPLETED, command);
            case AUTO_RESOLVE_MENTOR_NO_SHOW, AUTO_RESOLVE_MENTEE_NO_SHOW,
                    ADMIN_CONFIRM_SESSION, ADMIN_CONFIRM_MENTOR_NO_SHOW, ADMIN_CONFIRM_MENTEE_NO_SHOW ->
                    require(current, BookingStatus.UNDER_REVIEW, BookingStatus.COMPLETED, command);
        };
        return target;
    }

    public static boolean isTerminal(BookingStatus status) {
        return status == BookingStatus.REJECTED
                || status == BookingStatus.EXPIRED
                || status == BookingStatus.CANCELLED_BY_MENTEE
                || status == BookingStatus.CANCELLED_BY_MENTOR
                || status == BookingStatus.COMPLETED;
    }

    public static boolean isScheduled(BookingStatus status) {
        return status == BookingStatus.PAID
                || status == BookingStatus.AWAITING_MENTOR_COMPLETION
                || status == BookingStatus.AWAITING_MENTEE_CONFIRMATION;
    }

    private static BookingStatus require(BookingStatus actual, BookingStatus expected, BookingStatus target,
                                         BookingTransitionCommand command) {
        if (actual != expected) {
            throw invalid(actual, command);
        }
        return target;
    }

    private static BookingStatus requireAny(BookingStatus actual, BookingStatus[] expected, BookingStatus target,
                                            BookingTransitionCommand command) {
        for (BookingStatus status : expected) {
            if (status == actual) {
                return target;
            }
        }
        throw invalid(actual, command);
    }

    private static BaseException invalid(BookingStatus current, BookingTransitionCommand command) {
        return new BaseException(ErrorCode.RESOURCE_CONFLICT,
                "Không thể thực hiện " + command + " khi booking đang ở trạng thái " + current);
    }
}
