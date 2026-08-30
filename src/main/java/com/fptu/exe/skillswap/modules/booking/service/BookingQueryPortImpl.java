package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingQueryPortImpl implements BookingQueryPort {

    private final BookingRepository bookingRepository;

    @Override
    public long countByMentorProfileUserIdAndStatusIn(UUID mentorUserId, List<BookingStatus> statuses) {
        return bookingRepository.countByMentorUserIdAndStatusIn(mentorUserId, statuses);
    }

    @Override
    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    public List<Booking> findBookingsByIds(Collection<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) return List.of();
        return bookingRepository.findAllById(bookingIds);
    }

    @Override
    public boolean hasActiveBookings(UUID userId) {
        if (userId == null) return false;
        return bookingRepository.existsByMenteeIdAndStatusIn(userId, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.PAID, BookingStatus.UNDER_REVIEW));
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
    public Booking save(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    public boolean existsById(UUID bookingId) {
        return bookingRepository.existsById(bookingId);
    }

    @Override
    public long countByMenteeId(UUID menteeId) {
        return bookingRepository.countByMenteeId(menteeId);
    }

    @Override
    public long countByMentorProfileUserId(UUID mentorUserId) {
        return bookingRepository.countByMentorUserId(mentorUserId);
    }
}
