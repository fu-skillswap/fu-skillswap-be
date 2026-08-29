package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingQueryPortImpl implements BookingQueryPort {

    private final BookingRepository bookingRepository;

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
        return menteeId == null ? 0L : bookingRepository.countByMenteeId(menteeId);
    }

    @Override
    public long countByMentorProfileUserId(UUID mentorUserId) {
        return mentorUserId == null ? 0L : bookingRepository.countByMentorProfileUserId(mentorUserId);
    }
}
