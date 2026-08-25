package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The only writer for {@link BookingStatus} in production code.
 *
 * <p>It applies the state-machine decision and records timestamps owned by that transition.
 * Outcome, issue and settlement details remain the responsibility of their bounded workflow.</p>
 */
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

    /** Temporary compatibility bridge during the UTC dual-write rollout. */
    @Deprecated
    public static BookingStatus apply(Booking booking, BookingTransitionCommand command, LocalDateTime at) {
        if (booking == null || at == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking và thời điểm chuyển trạng thái là bắt buộc");
        }
        return apply(booking, command, at.atZone(BUSINESS_ZONE).toInstant());
    }
}
