package com.fptu.exe.skillswap.shared.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoUploadInitResponse;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackwardCompatibleDtoSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void uploadMetadataIsAdditiveAndKeepsLegacyUploadFields() throws Exception {
        ProviderNeutralUploadMetadata metadata = new ProviderNeutralUploadMetadata(
                null, UUID.fromString("019f1234-aaaa-bbbb-cccc-1234567890ab"),
                "https://upload.example/temporary", Instant.parse("2026-09-04T04:00:00Z"),
                "BLOG_IMAGE", Map.of("Content-Type", "image/png"));
        PublicAssetUploadPort.UploadIntent uploadIntent = new PublicAssetUploadPort.UploadIntent(
                metadata.uploadIntentId(), metadata.url(), LocalDateTime.of(2026, 9, 4, 11, 0),
                metadata.requiredHeaders(), metadata);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(uploadIntent));

        assertEquals(metadata.uploadIntentId().toString(), json.get("uploadIntentId").asText());
        assertEquals(metadata.url(), json.get("uploadUrl").asText());
        assertEquals("image/png", json.get("requiredHeaders").get("Content-Type").asText());
        assertEquals("BLOG_IMAGE", json.get("metadata").get("assetType").asText());
    }

    @Test
    void courseUploadKeepsProviderFieldsAndAddsNeutralMetadata() throws Exception {
        CourseVideoUploadInitResponse response = CourseVideoUploadInitResponse.builder()
                .materialId(UUID.fromString("019f1234-aaaa-bbbb-cccc-1234567890ab"))
                .bunnyLibraryId("123456")
                .bunnyVideoId("video-1")
                .uploadUrl("https://video-upload.example/temporary")
                .authorizationSignature("temporary-signature")
                .expirationTimestamp(1780000000L)
                .uploadMetadata(new ProviderNeutralUploadMetadata(
                        UUID.fromString("019f1234-aaaa-bbbb-cccc-1234567890ab"), null,
                        "https://video-upload.example/temporary", Instant.parse("2026-09-04T04:00:00Z"),
                        "COURSE_VIDEO", Map.of()))
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals("123456", json.get("bunnyLibraryId").asText());
        assertEquals("video-1", json.get("bunnyVideoId").asText());
        assertEquals("temporary-signature", json.get("authorizationSignature").asText());
        assertEquals("COURSE_VIDEO", json.get("uploadMetadata").get("assetType").asText());
    }

    @Test
    void userFacingStateFieldsSerializeWithoutProviderErrorDetails() throws Exception {
        PaymentCheckoutResponse payment = PaymentCheckoutResponse.builder()
                .status(PaymentOrderStatus.FAILED)
                .userActionMessage("Thanh toán chưa thành công. Bạn có thể bắt đầu lại theo hướng dẫn.")
                .retryable(true)
                .build();
        ConversationResponse chat = ConversationResponse.builder()
                .readOnlyReason(ChatReadOnlyReason.CHAT_WINDOW_EXPIRED)
                .userActionMessage(ConversationResponse.userActionMessage(ChatReadOnlyReason.CHAT_WINDOW_EXPIRED))
                .retryable(false)
                .build();
        CourseMaterialSummaryResponse course = CourseMaterialSummaryResponse.builder()
                .available(true)
                .userActionMessage("Tài liệu đang được xử lý. Vui lòng thử lại sau.")
                .retryable(true)
                .build();

        JsonNode paymentJson = objectMapper.readTree(objectMapper.writeValueAsString(payment));
        JsonNode chatJson = objectMapper.readTree(objectMapper.writeValueAsString(chat));
        JsonNode courseJson = objectMapper.readTree(objectMapper.writeValueAsString(course));

        assertTrue(paymentJson.get("retryable").asBoolean());
        assertEquals("Thanh toán chưa thành công. Bạn có thể bắt đầu lại theo hướng dẫn.",
                paymentJson.get("userActionMessage").asText());
        assertEquals("CHAT_WINDOW_EXPIRED", chatJson.get("readOnlyReason").asText());
        assertEquals("Tài liệu đang được xử lý. Vui lòng thử lại sau.", courseJson.get("userActionMessage").asText());
    }
}
