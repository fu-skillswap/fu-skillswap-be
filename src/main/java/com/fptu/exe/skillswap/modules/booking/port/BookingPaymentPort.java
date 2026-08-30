package com.fptu.exe.skillswap.modules.booking.port;

import java.util.UUID;

public interface BookingPaymentPort {
    void markBookingPaid(UUID bookingId, UUID paymentOrderId);
}
