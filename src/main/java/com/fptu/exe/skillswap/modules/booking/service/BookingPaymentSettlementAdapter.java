package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingCancellationContext;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingSettlementSnapshot;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Booking-owned adapter for payment cancellation and settlement context. */
@Service
@RequiredArgsConstructor
public class BookingPaymentSettlementAdapter implements BookingPaymentSettlementPort {

    private final BookingRepository bookingRepository;
    private final BookingPaymentQueryPort bookingPaymentQueryPort;

    @Override
    public Optional<BookingCancellationContext> findCancellationContext(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(this::toCancellationContext);
    }

    @Override
    public Optional<BookingSettlementSnapshot> findSettlementSnapshot(UUID bookingId) {
        return bookingPaymentQueryPort.findSettlementSnapshot(bookingId);
    }

    private BookingCancellationContext toCancellationContext(Booking booking) {
        Instant acceptedAtUtc = booking.getAcceptedAtUtc() != null
                ? booking.getAcceptedAtUtc() : BookingTime.toInstant(booking.getAcceptedAt());
        Instant selectedStartAtUtc = booking.getSelectedStartTimeUtc() != null
                ? booking.getSelectedStartTimeUtc() : BookingTime.toInstant(booking.getSelectedStartTime());
        Instant cancelledAtUtc = booking.getCancelledAtUtc() != null
                ? booking.getCancelledAtUtc() : BookingTime.toInstant(booking.getCancelledAt());

        long minutesUntilStart = selectedStartAtUtc == null || cancelledAtUtc == null
                ? Long.MAX_VALUE : Duration.between(cancelledAtUtc, selectedStartAtUtc).toMinutes();
        boolean cancelled = booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE
                || booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR;

        return new BookingCancellationContext(
                booking.getId(),
                booking.getMentee() == null ? null : booking.getMentee().getId(),
                booking.getMentorUserId(),
                booking.getStatus() == null ? null : booking.getStatus().name(),
                booking.getCancelReason(),
                acceptedAtUtc,
                selectedStartAtUtc,
                cancelledAtUtc,
                BookingDeadlinePolicy.isLateCancellation(minutesUntilStart),
                cancelled && acceptedAtUtc != null
        );
    }
}
