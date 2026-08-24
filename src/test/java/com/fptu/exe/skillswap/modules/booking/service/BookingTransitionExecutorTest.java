package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookingTransitionExecutorTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Test
    void autoCloseDoesNotSetFinalizedAtBeforeSessionFinalizationRuns() {
        Booking booking = Booking.builder().status(BookingStatus.AWAITING_MENTOR_COMPLETION).build();

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_CLOSE, now);

        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(now, booking.getAutoClosedAt());
        assertNull(booking.getFinalizedAt());
    }

    @Test
    void noShowResolutionUsesCompletedStatusAndFinalizationTimestamp() {
        Booking booking = Booking.builder().status(BookingStatus.UNDER_REVIEW).build();

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_RESOLVE_MENTOR_NO_SHOW, now);

        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(now, booking.getFinalizedAt());
    }

    @Test
    void cancellationOwnsOnlyCancellationTimestamp() {
        Booking booking = Booking.builder().status(BookingStatus.PAID).build();

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.CANCEL_BY_MENTEE, now);

        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, booking.getStatus());
        assertEquals(now, booking.getCancelledAt());
        assertNull(booking.getRejectedAt());
    }
}
