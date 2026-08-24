package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMachine;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

import java.time.LocalDateTime;

/** Applies the single state-machine decision and status-owned timestamps. */
public final class BookingTransitionExecutor {

    private BookingTransitionExecutor() {
    }

    public static BookingStatus apply(Booking booking, BookingTransitionCommand command, LocalDateTime at) {
        if (booking == null || at == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking và thời điểm chuyển trạng thái là bắt buộc");
        }
        BookingStatus target = BookingStateMachine.target(booking.getStatus(), command);
        booking.setStatus(target);
        switch (command) {
            case ACCEPT_FREE, ACCEPT_PAID -> booking.setAcceptedAt(at);
            case REJECT, SYSTEM_REJECT, EXPIRE_PENDING, EXPIRE_PAYMENT -> booking.setRejectedAt(at);
            case CANCEL_BY_MENTEE, CANCEL_BY_MENTOR -> booking.setCancelledAt(at);
            case MENTOR_COMPLETED -> booking.setCompletedAt(at);
            case AUTO_CLOSE -> booking.setAutoClosedAt(at);
            case AUTO_RESOLVE_MENTOR_NO_SHOW, AUTO_RESOLVE_MENTEE_NO_SHOW,
                    ADMIN_CONFIRM_MENTOR_NO_SHOW, ADMIN_CONFIRM_MENTEE_NO_SHOW -> booking.setFinalizedAt(at);
            default -> {
                // Outcome, issue and settlement-specific fields stay with the bounded caller.
            }
        }
        return target;
    }
}
