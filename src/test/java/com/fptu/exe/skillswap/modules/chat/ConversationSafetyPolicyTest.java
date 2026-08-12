package com.fptu.exe.skillswap.modules.chat;

import com.fptu.exe.skillswap.modules.booking.service.BookingChatAccessPolicy;
import com.fptu.exe.skillswap.modules.chat.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationUserBlockRepository;
import com.fptu.exe.skillswap.modules.chat.service.ConversationSafetyPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationSafetyPolicyTest {

    @Test
    void blockOverridesBookingEntitlementAndPreventsNewAttachmentDownloads() {
        ConversationUserBlockRepository blocks = mock(ConversationUserBlockRepository.class);
        UUID conversationId = UUID.randomUUID();
        when(blocks.existsByConversationId(conversationId)).thenReturn(true);
        ConversationSafetyPolicy policy = new ConversationSafetyPolicy(blocks);

        BookingChatAccessPolicy.Access access = new BookingChatAccessPolicy.Access(
                ChatMessagingAccess.OPEN, true, true, true, null, LocalDateTime.now().plusHours(1), true);

        BookingChatAccessPolicy.Access resolved = policy.apply(conversationId, access);

        assertEquals(ChatMessagingAccess.READ_ONLY, resolved.messagingAccess());
        assertEquals(ChatReadOnlyReason.PARTICIPANT_BLOCKED, resolved.readOnlyReason());
        assertFalse(resolved.canSendMessages());
        assertFalse(resolved.canUploadAttachments());
        assertFalse(resolved.canDownloadAttachments());
    }
}
