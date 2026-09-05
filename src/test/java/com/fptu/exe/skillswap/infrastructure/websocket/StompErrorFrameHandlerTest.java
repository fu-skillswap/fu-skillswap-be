package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StompErrorFrameHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StompErrorFrameHandler handler = new StompErrorFrameHandler(objectMapper);

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void shouldReturnStableUnauthorizedErrorFrameWithCorrelationHeader() throws Exception {
        Message<byte[]> clientMessage = clientMessage(StompCommand.SUBSCRIBE, "session-1", "user-1", "corr-123");

        Message<byte[]> response = handler.handleClientMessageProcessingError(
                clientMessage, new AccessDeniedException("database password must never be exposed"));

        JsonNode body = objectMapper.readTree(response.getPayload());
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(response);
        assertEquals(StompCommand.ERROR, headers.getCommand());
        assertEquals(ErrorCode.CHAT_ACCESS_DENIED.getCode(), body.get("code").asText());
        assertEquals(ErrorCode.CHAT_ACCESS_DENIED.getMessage(), body.get("message").asText());
        assertEquals("corr-123", headers.getFirstNativeHeader(TraceContext.CORRELATION_ID_HEADER));
        assertFalse(body.toString().contains("database password"));
        assertFalse(body.toString().contains("AccessDeniedException"));
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() throws Exception {
        Message<byte[]> clientMessage = clientMessage(StompCommand.SEND, "session-2", "user-2", null);

        Message<byte[]> response = handler.handleClientMessageProcessingError(
                clientMessage, new IllegalStateException("jdbc password=do-not-leak"));

        JsonNode body = objectMapper.readTree(response.getPayload());
        assertEquals(ErrorCode.CHAT_INTERNAL_ERROR.getCode(), body.get("code").asText());
        assertEquals(ErrorCode.CHAT_INTERNAL_ERROR.getMessage(), body.get("message").asText());
        assertFalse(body.toString().contains("jdbc password"));
        assertFalse(body.toString().contains("IllegalStateException"));
        assertNotNull(body.get("code"));
    }

    private Message<byte[]> clientMessage(StompCommand command, String sessionId, String userId, String correlationId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setUser(() -> userId);
        if (correlationId != null) {
            accessor.addNativeHeader(TraceContext.CORRELATION_ID_HEADER, correlationId);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
