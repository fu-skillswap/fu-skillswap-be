package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimePushService {

    private static final CloseStatus UNAUTHORIZED_CLOSE_STATUS = new CloseStatus(4401, "Unauthorized");

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public void pushAuthOk(WebSocketSession session, UUID userId) {
        sendToSession(session, RealtimeMessageType.AUTH_OK, new RealtimePayloads.AuthOkPayload(userId));
    }

    public void pushPong(WebSocketSession session) {
        sendToSession(session, RealtimeMessageType.PONG, null);
    }

    public void pushError(WebSocketSession session, String code, String message) {
        sendToSession(session, RealtimeMessageType.ERROR, new RealtimePayloads.ErrorPayload(code, message));
    }

    public void pushErrorAndCloseUnauthorized(WebSocketSession session, String code, String message) {
        pushError(session, code, message);
        closeUnauthorized(session);
    }

    public void pushToUser(UUID userId, String type, Object payload) {
        Set<WebSocketSession> sessions = sessionRegistry.getSessions(userId);
        if (sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendToSession(session, type, payload);
        }
    }

    public void closeUnauthorized(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(UNAUTHORIZED_CLOSE_STATUS);
            }
        } catch (IOException ex) {
            log.warn("Không thể đóng WebSocket unauthorized session {}", session.getId(), ex);
        }
    }

    private void sendToSession(WebSocketSession session, String type, Object payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(new RealtimeEnvelope(type, payload, DateTimeUtil.now()));
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException ex) {
            log.warn("Không thể gửi realtime message type={} tới session={}", type, session.getId(), ex);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record RealtimeEnvelope(String type, Object payload, LocalDateTime timestamp) {
    }
}
