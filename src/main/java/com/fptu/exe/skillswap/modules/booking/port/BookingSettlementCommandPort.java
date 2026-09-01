package com.fptu.exe.skillswap.modules.booking.port;

import java.util.UUID;

/** Booking-owned intent boundary for requesting Payment settlement operations. */
public interface BookingSettlementCommandPort {

    void requestBookingRelease(UUID bookingId);

    void requestAdminIssueResolution(UUID bookingId, UUID resolutionId);

    void requestResolutionReversal(UUID bookingId, UUID originalResolutionId, UUID reversalRecordId);

    void requestMentorNoShowRefund(UUID bookingId);
}
