package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.port.ChatPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatPortImpl implements ChatPort {

    private final ChatRoomService chatRoomService;

    @Override
    public UUID createBookingConversation(UUID bookingId, UUID menteeUserId, UUID mentorUserId) {
        Conversation conv = chatRoomService.createDirectForAcceptedBooking(bookingId, mentorUserId, menteeUserId);
        return conv != null ? conv.getId() : null;
    }

    @Override
    public Map<UUID, UUID> getConversationIdsForBookings(Collection<UUID> bookingIds) {
        return chatRoomService.getConversationIdsForBookings(bookingIds);
    }
}
