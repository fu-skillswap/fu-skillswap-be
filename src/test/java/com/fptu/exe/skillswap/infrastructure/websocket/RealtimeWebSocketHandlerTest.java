package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketHandlerTest {

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Mock
    private RealtimePushService realtimePushService;

    @Mock
    private WebSocketSession session;

    private RealtimeWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RealtimeWebSocketHandler(
                sessionRegistry,
                realtimePushService,
                new ObjectMapper()
        );
    }

    @Test
    void afterConnectionEstablished_withValidPrincipal_shouldRegisterAndPushAuthOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "user@test.com", List.of(RoleCode.MENTEE));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthHandshakeInterceptor.AUTHENTICATED_ATTRIBUTE, true);
        attributes.put(WebSocketAuthHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
        attributes.put(WebSocketAuthHandshakeInterceptor.USER_PRINCIPAL_ATTRIBUTE, principal);

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-1");

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).register(userId, session);
        verify(realtimePushService).pushAuthOk(session, userId);
    }

    @Test
    void afterConnectionEstablished_withoutPrincipal_shouldCloseUnauthorized() throws Exception {
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(session);

        verify(realtimePushService).pushErrorAndCloseUnauthorized(session, "WS_4401", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        verify(sessionRegistry, never()).register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleTextMessage_ping_shouldReplyPong() throws Exception {
        Method method = RealtimeWebSocketHandler.class
                .getDeclaredMethod("handleTextMessage", WebSocketSession.class, TextMessage.class);
        method.setAccessible(true);

        method.invoke(handler, session, new TextMessage("{\"type\":\"PING\"}"));

        verify(realtimePushService).pushPong(session);
        verify(realtimePushService, never()).pushError(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handleTextMessage_invalidJson_shouldPushError() throws Exception {
        Method method = RealtimeWebSocketHandler.class
                .getDeclaredMethod("handleTextMessage", WebSocketSession.class, TextMessage.class);
        method.setAccessible(true);

        method.invoke(handler, session, new TextMessage("{not-json"));

        verify(realtimePushService).pushError(session, "WS_4001", "Realtime payload không hợp lệ");
    }
}
