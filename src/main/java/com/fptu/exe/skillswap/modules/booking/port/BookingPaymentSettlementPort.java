package com.fptu.exe.skillswap.modules.booking.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Booking-owned read boundary for payment cancellation and settlement workflows.
 * Only immutable contract values cross this boundary.
 */
public interface BookingPaymentSettlementPort {

    Optional<BookingCancellationContext> findCancellationContext(UUID bookingId);

    Optional<BookingSettlementSnapshot> findSettlementSnapshot(UUID bookingId);
}
