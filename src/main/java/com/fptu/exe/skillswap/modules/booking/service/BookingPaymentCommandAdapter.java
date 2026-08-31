package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentCommandPort;
import com.fptu.exe.skillswap.modules.booking.port.PaymentConfirmationResult;
import com.fptu.exe.skillswap.modules.booking.port.PaymentExpiryResult;
import com.fptu.exe.skillswap.modules.booking.port.SettlementResult;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Booking-owned adapter for payment lifecycle commands. */
@Service
@RequiredArgsConstructor
public class BookingPaymentCommandAdapter implements BookingPaymentCommandPort {
    private final BookingRepository bookingRepository;
    private final BookingEventService bookingEventService;

    @Override
    @Transactional
    public PaymentConfirmationResult confirmPayment(UUID bookingId, Instant confirmedAtUtc) {
        Booking booking = lockedBooking(bookingId);
        String previousStatus = booking.getStatus() == null ? null : booking.getStatus().name();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.PAYMENT_CONFIRMED, confirmedAtUtc);
        Booking saved = bookingRepository.save(booking);
        return new PaymentConfirmationResult(bookingId, previousStatus,
                saved.getStatus() == null ? null : saved.getStatus().name(), confirmedAtUtc);
    }

    @Override
    @Transactional
    public PaymentExpiryResult expirePayment(UUID bookingId, Instant expiredAtUtc) {
        Booking booking = lockedBooking(bookingId);
        String previousStatus = booking.getStatus() == null ? null : booking.getStatus().name();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.EXPIRE_PAYMENT, expiredAtUtc);
        Booking saved = bookingRepository.save(booking);
        return new PaymentExpiryResult(bookingId, previousStatus,
                saved.getStatus() == null ? null : saved.getStatus().name(), expiredAtUtc);
    }

    @Override
    @Transactional
    public SettlementResult completeSettlement(UUID bookingId, Instant completedAtUtc) {
        Booking booking = lockedBooking(bookingId);
        bookingEventService.record(
                booking,
                BookingEventType.SETTLEMENT_RELEASED,
                booking.getStatus(),
                BookingEventActorType.SYSTEM,
                null,
                "{\"completedAtUtc\":\"" + completedAtUtc + "\"}");
        return new SettlementResult(bookingId, booking.getMentorUserId(),
                booking.getServicePriceScoinSnapshot(), "COMPLETED", completedAtUtc);
    }

    private Booking lockedBooking(UUID bookingId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("bookingId is required");
        }
        return bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }
}
