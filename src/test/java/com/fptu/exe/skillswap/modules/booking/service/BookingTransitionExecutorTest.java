package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookingTransitionExecutorTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);
    private final Instant nowUtc = Instant.parse("2026-08-24T03:00:00Z");

    @Test
    void autoCloseDoesNotSetFinalizedAtBeforeSessionFinalizationRuns() {
        Booking booking = Booking.builder().status(BookingStatus.AWAITING_MENTOR_COMPLETION).build();

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_CLOSE, now);

        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(now, booking.getAutoClosedAt());
        assertEquals(nowUtc, booking.getAutoClosedAtUtc());
        assertNull(booking.getFinalizedAt());
    }

    @Test
    void noShowResolutionUsesCompletedStatusAndFinalizationTimestamp() {
        Booking booking = Booking.builder().status(BookingStatus.UNDER_REVIEW).build();

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_RESOLVE_MENTOR_NO_SHOW, nowUtc);

        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(nowUtc, booking.getFinalizedAtUtc());
        assertEquals(now, booking.getFinalizedAt());
    }

    @Test
    void cancellationOwnsOnlyCancellationTimestamp() {
        Booking booking = Booking.builder().status(BookingStatus.PAID).build();

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.CANCEL_BY_MENTEE, nowUtc);

        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, booking.getStatus());
        assertEquals(nowUtc, booking.getCancelledAtUtc());
        assertEquals(now, booking.getCancelledAt());
        assertNull(booking.getRejectedAt());
    }

    @Test
    void acceptPopulatesAcceptedAtAndAcceptedAtUtc() {
        Booking booking = Booking.builder().status(BookingStatus.PENDING).build();

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ACCEPT_PAID, nowUtc);

        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, booking.getStatus());
        assertEquals(nowUtc, booking.getAcceptedAtUtc());
        assertEquals(now, booking.getAcceptedAt());
    }
}
