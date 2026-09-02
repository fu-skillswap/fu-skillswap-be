package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;

/** Narrow query boundary for Booking-owned mentor booking eligibility rules. */
public interface BookingEligibilityQueryPort {

    boolean isDiscoverableMentorForBooking(MentorBookingCapability capability);
}
