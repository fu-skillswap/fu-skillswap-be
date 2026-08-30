package com.fptu.exe.skillswap.modules.chat.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface ChatPort {
    UUID createBookingConversation(UUID bookingId, UUID menteeUserId, UUID mentorUserId);
    Map<UUID, UUID> getConversationIdsForBookings(Collection<UUID> bookingIds);
}
