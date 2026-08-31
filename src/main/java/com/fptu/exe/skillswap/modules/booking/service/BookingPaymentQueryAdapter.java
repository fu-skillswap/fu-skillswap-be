package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSnapshot;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Booking-owned adapter that prevents payment consumers from seeing Booking persistence types. */
@Service
@RequiredArgsConstructor
public class BookingPaymentQueryAdapter implements BookingPaymentQueryPort {
    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingPaymentSnapshot> findPaymentSnapshot(UUID bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        return bookingRepository.findById(bookingId).map(this::toSnapshot);
    }

    private BookingPaymentSnapshot toSnapshot(Booking booking) {
        Instant selectedStartAtUtc = booking.getSelectedStartTimeUtc();
        if (selectedStartAtUtc == null && booking.getSlot() != null) {
            selectedStartAtUtc = booking.getSlot().getStartTimeUtc();
        }
        if (selectedStartAtUtc == null) {
            selectedStartAtUtc = booking.getSelectedStartTime() == null
                    ? null
                    : com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(booking.getSelectedStartTime());
        }

        Instant acceptedAtUtc = booking.getAcceptedAtUtc();
        if (acceptedAtUtc == null && booking.getAcceptedAt() != null) {
            acceptedAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(booking.getAcceptedAt());
        }

        var mentee = booking.getMentee();
        return new BookingPaymentSnapshot(
                booking.getId(),
                mentee == null ? null : mentee.getId(),
                booking.getMentorUserId(),
                booking.getServiceId(),
                booking.getServicePriceScoinSnapshot(),
                Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot()),
                booking.getStatus() == null ? null : booking.getStatus().name(),
                acceptedAtUtc,
                selectedStartAtUtc,
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAtUtc, selectedStartAtUtc)
        );
    }
}
