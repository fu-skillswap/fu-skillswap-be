package com.fptu.exe.skillswap.modules.booking.port;

import java.util.UUID;

/**
 * Retires booking offers for a mentor service that is being deactivated.
 * The Booking module owns pending-booking transitions and slot bindings.
 */
public interface MentorServiceRetirementPort {

    void retireFutureOffers(UUID mentorUserId, UUID serviceId, boolean rejectPendingBookings);
}
