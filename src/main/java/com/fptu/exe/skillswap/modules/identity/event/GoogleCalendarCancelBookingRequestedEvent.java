package com.fptu.exe.skillswap.modules.identity.event;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;

import java.util.UUID;

public record GoogleCalendarCancelBookingRequestedEvent(UUID bookingId, BookingStatus status) {
}
