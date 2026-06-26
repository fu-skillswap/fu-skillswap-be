package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final RealtimePushService realtimePushService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = (UUID) session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_ID_ATTRIBUTE);
        UserPrincipal principal = (UserPrincipal) session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_PRINCIPAL_ATTRIBUTE);
        if (userId == null || principal == null) {
            realtimePushService.closeUnauthorized(session);
            return;
        }
        sessionRegistry.register(userId, session);
        realtimePushService.pushAuthOk(session, userId);
        log.info("Raw WebSocket connected for userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        JsonNode typeNode = root.get("type");
        String type = typeNode == null ? null : typeNode.asText(null);
        if (RealtimeMessageType.PING.equals(type)) {
            realtimePushService.pushPong(session);
            return;
        }
        realtimePushService.pushError(session, "WS_4000", "Loại realtime message không được hỗ trợ");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        UUID userId = (UUID) session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_ID_ATTRIBUTE);
        sessionRegistry.unregister(userId, session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = (UUID) session.getAttributes().get(WebSocketAuthHandshakeInterceptor.USER_ID_ATTRIBUTE);
        sessionRegistry.unregister(userId, session);
        log.info("Raw WebSocket closed for userId={}, sessionId={}, status={}", userId, session.getId(), status);
    }
}
