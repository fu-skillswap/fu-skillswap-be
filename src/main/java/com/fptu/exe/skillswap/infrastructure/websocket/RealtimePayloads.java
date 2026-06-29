package com.fptu.exe.skillswap.infrastructure.websocket;

import java.util.UUID;

public final class RealtimePayloads {

    private RealtimePayloads() {
    }

    public record AuthOkPayload(UUID userId) {
    }

    public record ErrorPayload(String code, String message) {
    }

    public record NotificationBadgePayload(long unreadCount, String eventKind) {
    }
}
