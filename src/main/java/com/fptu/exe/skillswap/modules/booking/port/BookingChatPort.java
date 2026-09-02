package com.fptu.exe.skillswap.modules.booking.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Narrow chat capability consumed by Booking without exposing Chat internals. */
public interface BookingChatPort {

    UUID createDirectForAcceptedBooking(UUID bookingId, UUID mentorUserId, UUID menteeUserId);

    Map<UUID, UUID> findConversationIdsByBookingIds(List<UUID> bookingIds);
}
