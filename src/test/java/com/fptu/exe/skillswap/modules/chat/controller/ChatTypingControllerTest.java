package com.fptu.exe.skillswap.modules.chat.controller;

import com.fptu.exe.skillswap.modules.chat.dto.request.ChatTypingRequest;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.infrastructure.websocket.StompErrorException;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatTypingControllerTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ObjectProvider<org.springframework.messaging.simp.SimpMessagingTemplate> templateProvider = mock(ObjectProvider.class);
    private final InMemoryRateLimitService rateLimitService = mock(InMemoryRateLimitService.class);
    private final ChatTypingController controller = new ChatTypingController(
            conversationService, templateProvider, rateLimitService);

    @Test
    void shouldRejectMissingTypingPayloadAsInvalidMessage() {
        StompErrorException error = assertThrows(StompErrorException.class,
                () -> controller.typing(null, principal(UUID.randomUUID())));

        assertEquals(ErrorCode.CHAT_INVALID_MESSAGE, error.getErrorCode());
    }

    @Test
    void shouldRejectTypingFromNonParticipant() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(conversationService.isParticipant(conversationId, userId)).thenReturn(false);

        StompErrorException error = assertThrows(StompErrorException.class,
                () -> controller.typing(new ChatTypingRequest(conversationId, true), principal(userId)));

        assertEquals(ErrorCode.CHAT_ACCESS_DENIED, error.getErrorCode());
    }

    private Principal principal(UUID userId) {
        return () -> userId.toString();
    }
}
