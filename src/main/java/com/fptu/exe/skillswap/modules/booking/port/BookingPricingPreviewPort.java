package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;

import java.util.UUID;

/** Booking-owned outbound pricing query contract. Implemented by Payment module. */
public interface BookingPricingPreviewPort {

    String ESTIMATE_DISCLAIMER = "Final price is calculated at checkout.";

    BookingPricingEstimate estimateForCandidate(UUID viewerUserId, ServiceSlotCandidate candidate);
}
