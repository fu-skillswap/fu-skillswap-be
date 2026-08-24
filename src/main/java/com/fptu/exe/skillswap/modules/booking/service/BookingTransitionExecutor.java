package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMachine;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Applies the single state-machine decision and status-owned timestamps. */
public final class BookingTransitionExecutor {

    private static final ZoneId BUSINESS_ZONE = TimeProvider.BUSINESS_ZONE;

    private BookingTransitionExecutor() {
    }

    public static BookingStatus apply(Booking booking, BookingTransitionCommand command, Instant atUtc) {
        if (booking == null || atUtc == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking và thời điểm chuyển trạng thái là bắt buộc");
        }
        BookingStatus target = BookingStateMachine.target(booking.getStatus(), command);
        booking.setStatus(target);
        LocalDateTime atBusiness = LocalDateTime.ofInstant(atUtc, BUSINESS_ZONE);
        switch (command) {
            case ACCEPT_FREE, ACCEPT_PAID -> {
                booking.setAcceptedAtUtc(atUtc);
                booking.setAcceptedAt(atBusiness);
            }
            case REJECT, SYSTEM_REJECT, EXPIRE_PENDING, EXPIRE_PAYMENT -> {
                booking.setRejectedAtUtc(atUtc);
                booking.setRejectedAt(atBusiness);
            }
            case CANCEL_BY_MENTEE, CANCEL_BY_MENTOR -> {
                booking.setCancelledAtUtc(atUtc);
                booking.setCancelledAt(atBusiness);
            }
            case MENTOR_COMPLETED -> {
                booking.setCompletedAtUtc(atUtc);
                booking.setCompletedAt(atBusiness);
            }
            case AUTO_CLOSE -> {
                booking.setAutoClosedAtUtc(atUtc);
                booking.setAutoClosedAt(atBusiness);
            }
            case AUTO_RESOLVE_MENTOR_NO_SHOW, AUTO_RESOLVE_MENTEE_NO_SHOW,
                    ADMIN_CONFIRM_MENTOR_NO_SHOW, ADMIN_CONFIRM_MENTEE_NO_SHOW -> {
                booking.setFinalizedAtUtc(atUtc);
                booking.setFinalizedAt(atBusiness);
            }
            default -> {
                // Outcome, issue and settlement-specific fields stay with the bounded caller.
            }
        }
        return target;
    }

    public static BookingStatus apply(Booking booking, BookingTransitionCommand command, LocalDateTime at) {
        if (booking == null || at == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking và thời điểm chuyển trạng thái là bắt buộc");
        }
        Instant atUtc = at.atZone(BUSINESS_ZONE).toInstant();
        return apply(booking, command, atUtc);
    }
}
