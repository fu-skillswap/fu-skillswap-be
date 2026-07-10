package com.fptu.exe.skillswap.infrastructure.websocket;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StompSessionRegistry {

    private final ConcurrentHashMap<String, UUID> userIdBySessionId = new ConcurrentHashMap<>();

    public void register(String sessionId, UUID userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            return;
        }
        userIdBySessionId.put(sessionId, userId);
    }

    public UUID remove(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return userIdBySessionId.remove(sessionId);
    }

    public int size() {
        return userIdBySessionId.size();
    }
}
