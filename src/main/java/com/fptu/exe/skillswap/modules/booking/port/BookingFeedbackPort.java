package com.fptu.exe.skillswap.modules.booking.port;

import java.util.UUID;

public interface BookingFeedbackPort {
    boolean hasSubmittedFeedback(UUID bookingId, UUID reviewerId);
}
