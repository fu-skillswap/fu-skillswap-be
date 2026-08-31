package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/** Public lifecycle command contract exposed by Booking to Payment. */
public interface BookingPaymentCommandPort {

    void confirmPayment(UUID bookingId, Instant confirmedAtUtc);

    void expirePayment(UUID bookingId, Instant expiredAtUtc);

    void completeSettlement(UUID bookingId, Instant completedAtUtc);
}
