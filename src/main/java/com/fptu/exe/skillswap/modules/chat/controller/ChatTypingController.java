package com.fptu.exe.skillswap.modules.chat.controller;

import com.fptu.exe.skillswap.modules.chat.dto.request.ChatTypingRequest;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Hidden
public class ChatTypingController {
    private final ConversationService conversationService;
    private final ObjectProvider<SimpMessagingTemplate> simpMessagingTemplateProvider;
    private final InMemoryRateLimitService rateLimitService;

    @MessageMapping("chat/typing")
    public void typing(ChatTypingRequest request, Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        if (request == null || request.conversationId() == null || !conversationService.isParticipant(request.conversationId(), senderId)) return;
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.BEST_EFFORT, "chat:typing:" + senderId + ":" + request.conversationId(), 20, Duration.ofSeconds(10), "Typing quá nhanh");
        SimpMessagingTemplate template = simpMessagingTemplateProvider.getIfAvailable();
        if (template != null) {
            conversationService.getConversationParticipantUserIds(request.conversationId()).stream().filter(id -> !id.equals(senderId))
                    .forEach(id -> template.convertAndSendToUser(id.toString(), "/queue/chat/typing", new TypingPayload(request.conversationId(), senderId, request.typing())));
        }
    }
    public record TypingPayload(UUID conversationId, UUID senderId, boolean typing) {}
}
