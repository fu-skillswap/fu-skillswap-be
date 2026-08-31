package com.fptu.exe.skillswap.modules.booking.port;

import java.util.Optional;
import java.util.UUID;

/** Public read contract exposed by Booking to Payment. */
public interface BookingPaymentQueryPort {

    Optional<BookingPaymentSnapshot> findPaymentSnapshot(UUID bookingId);

    Optional<BookingSettlementSnapshot> findSettlementSnapshot(UUID bookingId);
}
