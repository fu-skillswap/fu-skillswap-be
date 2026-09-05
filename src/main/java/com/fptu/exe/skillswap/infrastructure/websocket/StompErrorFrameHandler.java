package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * Converts inbound STOMP failures into a stable, sanitized ERROR frame.
 * REST's ApiResponse envelope is deliberately not used for STOMP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompErrorFrameHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        ErrorCode errorCode = resolveErrorCode(exception);
        String correlationId = resolveCorrelationId(clientMessage);
        StompHeaderAccessor clientAccessor = clientMessage == null
                ? null : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);

        String sessionId = clientAccessor == null ? null : clientAccessor.getSessionId();
        String userId = clientAccessor == null || clientAccessor.getUser() == null
                ? null : clientAccessor.getUser().getName();
        String command = clientAccessor == null || clientAccessor.getCommand() == null
                ? null : clientAccessor.getCommand().name();
        String destination = clientAccessor == null ? null : clientAccessor.getDestination();

        if (errorCode.getStatus() < 500) {
            log.warn("STOMP business failure code={} correlationId={} sessionId={} userId={} command={} destination={}",
                    errorCode.getCode(), correlationId, sessionId, userId, command, destination);
        } else {
            log.error("STOMP system failure code={} correlationId={} sessionId={} userId={} command={} destination={}",
                    errorCode.getCode(), correlationId, sessionId, userId, command, destination, exception);
        }

        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setMessage(errorCode.getMessage());
        if (clientAccessor != null && clientAccessor.getReceipt() != null) {
            errorAccessor.setReceiptId(clientAccessor.getReceipt());
        }
        errorAccessor.setNativeHeader(TraceContext.CORRELATION_ID_HEADER, correlationId);
        errorAccessor.setNativeHeader(TraceContext.TRACE_ID_HEADER, correlationId);
        errorAccessor.setLeaveMutable(true);

        byte[] payload = serialize(new StompErrorPayload(errorCode.getCode(), errorCode.getMessage()));
        return MessageBuilder.createMessage(payload, errorAccessor.getMessageHeaders());
    }

    private ErrorCode resolveErrorCode(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof StompErrorException stompError) {
                return stompError.getErrorCode();
            }
            if (current instanceof BaseException baseException
                    && baseException.getErrorCode().getStatus() < 500) {
                return baseException.getErrorCode();
            }
            current = current.getCause();
        }
        if (hasType(exception, AccessDeniedException.class)) {
            return ErrorCode.CHAT_ACCESS_DENIED;
        }
        if (hasType(exception, IllegalArgumentException.class)
                || hasType(exception, MessageConversionException.class)) {
            return ErrorCode.CHAT_INVALID_MESSAGE;
        }
        return ErrorCode.CHAT_INTERNAL_ERROR;
    }

    private boolean hasType(Throwable exception, Class<? extends Throwable> expectedType) {
        Throwable current = exception;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveCorrelationId(Message<byte[]> clientMessage) {
        StompHeaderAccessor accessor = clientMessage == null
                ? null : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
        String correlationHeader = firstNativeHeader(accessor, TraceContext.CORRELATION_ID_HEADER);
        String legacyHeader = firstNativeHeader(accessor, TraceContext.TRACE_ID_HEADER);
        if (StringUtils.hasText(correlationHeader) || StringUtils.hasText(legacyHeader)) {
            return TraceContext.resolveAndSet(correlationHeader, legacyHeader);
        }
        String current = TraceContext.getCurrentTraceId();
        return StringUtils.hasText(current) ? current : "unavailable";
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        if (accessor == null || accessor.getNativeHeader(name) == null || accessor.getNativeHeader(name).isEmpty()) {
            return null;
        }
        return accessor.getNativeHeader(name).getFirst();
    }

    private byte[] serialize(StompErrorPayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            log.error("Unable to serialize STOMP error frame", exception);
            return ("{\"code\":\"" + ErrorCode.CHAT_INTERNAL_ERROR.getCode()
                    + "\",\"message\":\"" + ErrorCode.CHAT_INTERNAL_ERROR.getMessage() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private record StompErrorPayload(String code, String message) {
    }
}
