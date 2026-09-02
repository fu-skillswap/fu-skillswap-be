package com.fptu.exe.skillswap.modules.booking.port;

import java.util.Optional;
import java.util.UUID;

/** Query boundary used by Payment to obtain checkout-safe Booking facts. */
public interface BookingCheckoutQueryPort {

    Optional<BookingCheckoutSnapshot> findCheckoutSnapshot(UUID bookingId);

    Optional<BookingCheckoutSnapshot> findCheckoutSnapshotForUpdate(UUID bookingId);
}
