package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingChatAccessPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingQueryPortImpl implements BookingQueryPort, BookingChatAccessPort, BookingCheckoutQueryPort {

    private final BookingRepository bookingRepository;

    @Override
    public java.util.List<BookingChatAccessPort.ChatAccessSnapshot> findChatAccessSnapshots(java.util.Collection<java.util.UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return java.util.List.of();
        }
        return bookingRepository.findAllById(bookingIds).stream()
                .map(booking -> new BookingChatAccessPort.ChatAccessSnapshot(
                        booking.getId(),
                        booking.getStatus() == null ? null : booking.getStatus().name(),
                        booking.getCompletionOutcome() == null ? null : booking.getCompletionOutcome().name(),
                        booking.isMaintainPostSessionChatSnapshot(),
                        booking.getSelectedEndTime()
                ))
                .toList();
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        return bookingRepository.findById(bookingId);
    }

    @Override
    public Optional<Booking> findByIdForSessionUpdate(UUID bookingId) {
        return bookingRepository.findByIdForSessionUpdate(bookingId);
    }

    @Override
    public Optional<BookingCheckoutSnapshot> findCheckoutSnapshot(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(this::toCheckoutSnapshot);
    }

    @Override
    public Optional<BookingCheckoutSnapshot> findCheckoutSnapshotForUpdate(UUID bookingId) {
        return bookingRepository.findByIdForSessionUpdate(bookingId).map(this::toCheckoutSnapshot);
    }

    @Override
    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    public boolean existsById(UUID bookingId) {
        return bookingRepository.existsById(bookingId);
    }

    @Override
    public long countByMenteeId(UUID menteeId) {
        return menteeId == null ? 0L : bookingRepository.countByMenteeUserId(menteeId);
    }

    @Override
    public long countByMentorProfileUserId(UUID mentorUserId) {
        return mentorUserId == null ? 0L : bookingRepository.countByMentorUserId(mentorUserId);
    }

    private BookingCheckoutSnapshot toCheckoutSnapshot(Booking booking) {
        return new BookingCheckoutSnapshot(
                booking.getId(),
                booking.getMenteeUserId(),
                booking.getMentorUserId(),
                booking.getServiceId(),
                booking.getServiceIsFreeSnapshot(),
                booking.getServicePriceScoinSnapshot(),
                booking.getServiceDurationSnapshot(),
                booking.getServiceTitleSnapshot(),
                booking.getStatus() == null ? null : booking.getStatus().name(),
                BookingDeadlinePolicy.resolvePaymentDeadlineUtc(booking)
        );
    }
}
