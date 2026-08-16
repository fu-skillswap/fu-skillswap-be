package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;

import java.util.Optional;
import java.util.UUID;

public interface BookingQueryPort {

    Optional<Booking> findById(UUID bookingId);

    Optional<Booking> findByIdForSessionUpdate(UUID bookingId);

    Booking save(Booking booking);

    boolean existsById(UUID bookingId);
}
