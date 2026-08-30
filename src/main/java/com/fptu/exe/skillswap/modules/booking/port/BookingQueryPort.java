package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingQueryPort {
    long countByMentorProfileUserIdAndStatusIn(UUID mentorUserId, List<BookingStatus> statuses);
    Booking saveBooking(Booking booking);
    List<Booking> findBookingsByIds(Collection<UUID> bookingIds);
    boolean hasActiveBookings(UUID userId);
    Optional<Booking> findById(UUID bookingId);
    Optional<Booking> findByIdForSessionUpdate(UUID bookingId);
    Booking save(Booking booking);
    boolean existsById(UUID bookingId);
    long countByMenteeId(UUID menteeId);
    long countByMentorProfileUserId(UUID mentorUserId);
}
