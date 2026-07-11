package com.fptu.exe.skillswap.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompSessionLifecycleListener {

    private final StompSessionRegistry stompSessionRegistry;

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        UUID userId = resolveUserId(event.getUser());
        if (sessionId != null && userId != null) {
            stompSessionRegistry.register(sessionId, userId);
            log.debug("Registered STOMP session {} for user {}", sessionId, userId);
        }
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        UUID removedUserId = stompSessionRegistry.remove(sessionId);
        if (sessionId != null) {
            log.debug("Removed STOMP session {} for user {}", sessionId, removedUserId);
        }
    }

    private UUID resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
