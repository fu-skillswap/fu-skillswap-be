package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingChatPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chat adapter implementing the narrow capability exposed to Booking. */
@Component
@RequiredArgsConstructor
public class ChatBookingAdapter implements BookingChatPort {

    private final ChatService chatService;

    @Override
    public UUID createDirectForAcceptedBooking(UUID bookingId, UUID mentorUserId, UUID menteeUserId) {
        return chatService.createDirectForAcceptedBooking(bookingId, mentorUserId, menteeUserId).getId();
    }

    @Override
    public Map<UUID, UUID> findConversationIdsByBookingIds(List<UUID> bookingIds) {
        return chatService.findConversationIdsByBookingIds(bookingIds);
    }
}
