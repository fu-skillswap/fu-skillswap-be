package com.fptu.exe.skillswap.modules.conversation.service;

import com.fptu.exe.skillswap.modules.booking.service.BookingChatAccessPolicy;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationUserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Applies user safety controls after the booking domain has derived chat entitlement. */
@Component
@RequiredArgsConstructor
public class ConversationSafetyPolicy {

    private final ConversationUserBlockRepository conversationUserBlockRepository;

    public BookingChatAccessPolicy.Access apply(UUID conversationId, BookingChatAccessPolicy.Access bookingAccess) {
        if (!conversationUserBlockRepository.existsByConversationId(conversationId)) {
            return bookingAccess;
        }
        // A block preserves text history but stops both directions and new file URL issuance.
        return new BookingChatAccessPolicy.Access(
                ChatMessagingAccess.READ_ONLY,
                false,
                false,
                false,
                ChatReadOnlyReason.PARTICIPANT_BLOCKED,
                bookingAccess.messagingWindowEndsAt(),
                bookingAccess.postSessionChatPermanent()
        );
    }
}
