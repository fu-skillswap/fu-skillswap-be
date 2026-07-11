package com.fptu.exe.skillswap.infrastructure.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeDlqErrorLogger {

    private final ObjectMapper objectMapper;

    public void logDeadLetter(Message message, Throwable cause, String queueName) {
        MessageProperties properties = message.getMessageProperties();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("level", "ERROR");
        payload.put("type", "DLQ");
        payload.put("module", "realtime");
        payload.put("routingKey", properties.getReceivedRoutingKey());
        payload.put("eventType", header(properties, "x-event-type"));
        payload.put("aggregateType", header(properties, "x-aggregate-type"));
        payload.put("aggregateId", header(properties, "x-aggregate-id"));
        payload.put("outboxId", header(properties, "x-outbox-id"));
        payload.put("attemptCount", header(properties, "x-outbox-attempt-count"));
        payload.put("queueName", queueName);
        payload.put("errorSummary", summarize(cause));
        payload.put("occurredAt", DateTimeUtil.now().toString());

        try {
            log.error(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.error("{{\"level\":\"ERROR\",\"type\":\"DLQ\",\"module\":\"realtime\",\"queueName\":\"{}\",\"errorSummary\":\"{}\"}}",
                    queueName, summarize(cause));
        }
    }

    private Object header(MessageProperties properties, String key) {
        return properties.getHeaders().get(key);
    }

    private String summarize(Throwable cause) {
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return "Unknown consumer failure";
        }
        String message = cause.getMessage().replace("\"", "'");
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
