package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/** Public lifecycle command contract exposed by Booking to Payment. */
public interface BookingPaymentCommandPort {

    PaymentConfirmationResult confirmPayment(UUID bookingId, Instant confirmedAtUtc);

    PaymentExpiryResult expirePayment(UUID bookingId, Instant expiredAtUtc);

    SettlementResult completeSettlement(UUID bookingId, Instant completedAtUtc);
}
