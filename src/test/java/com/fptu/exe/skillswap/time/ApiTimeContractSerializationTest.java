package com.fptu.exe.skillswap.time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;
import com.fptu.exe.skillswap.modules.chat.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseAnnouncementResponse;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiTimeContractSerializationTest {

    private static final Instant SAMPLE = Instant.parse("2026-09-03T03:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void genericEnvelopeSerializesTimestampAsExplicitUtcInstant() throws Exception {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .timestamp(SAMPLE)
                .status(200)
                .code("SUCCESS_0200")
                .message("ok")
                .data("value")
                .build();

        assertEquals(SAMPLE.toString(), objectMapper.readTree(objectMapper.writeValueAsString(response))
                .get("timestamp").asText());
    }

    @Test
    void chatRestAndWebsocketPayloadsKeepTimezoneInformation() throws Exception {
        MessageResponse message = MessageResponse.builder()
                .id(UUID.randomUUID())
                .sequence(7L)
                .conversationId(UUID.randomUUID())
                .state(null)
                .createdAt(SAMPLE)
                .attachments(java.util.List.of())
                .build();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(message.conversationId())
                .messageId(message.id())
                .sequence(message.sequence())
                .createdAt(SAMPLE)
                .build();

        String messageJson = objectMapper.writeValueAsString(message);
        String eventJson = objectMapper.writeValueAsString(event);

        assertEquals(SAMPLE.toString(), objectMapper.readTree(messageJson).get("createdAt").asText());
        assertEquals(SAMPLE.toString(), objectMapper.readTree(eventJson).get("createdAt").asText());
        assertEquals(7L, objectMapper.readTree(eventJson).get("sequence").asLong());
    }

    @Test
    void notificationAndCourseAnnouncementTimestampsAreExplicit() throws Exception {
        NotificationResponse notification = NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .createdAt(SAMPLE)
                .readAt(SAMPLE.plusSeconds(60))
                .build();
        CourseAnnouncementResponse announcement = new CourseAnnouncementResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Title", "Content", SAMPLE, SAMPLE, SAMPLE);

        var notificationJson = objectMapper.readTree(objectMapper.writeValueAsString(notification));
        var announcementJson = objectMapper.readTree(objectMapper.writeValueAsString(announcement));

        assertEquals(SAMPLE.toString(), notificationJson.get("createdAt").asText());
        assertEquals(SAMPLE.plusSeconds(60).toString(), notificationJson.get("readAt").asText());
        assertEquals(SAMPLE.toString(), announcementJson.get("createdAt").asText());
        assertEquals(SAMPLE.toString(), announcementJson.get("publishedAt").asText());
    }
}
