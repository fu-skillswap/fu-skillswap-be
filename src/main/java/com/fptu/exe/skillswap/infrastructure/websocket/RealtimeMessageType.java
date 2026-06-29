package com.fptu.exe.skillswap.infrastructure.websocket;

public final class RealtimeMessageType {

    public static final String AUTH_OK = "AUTH_OK";
    public static final String ERROR = "ERROR";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final String CHAT_MESSAGE_CREATED = "CHAT_MESSAGE_CREATED";
    public static final String NEW_NOTIFICATION = "NEW_NOTIFICATION";
    public static final String NOTIFICATION_BADGE_UPDATED = "NOTIFICATION_BADGE_UPDATED";

    private RealtimeMessageType() {
    }
}
