package com.fptu.exe.skillswap.modules.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Payload for sending a text message into an existing conversation that the current user participates in.")
public record SendMessageRequest(
        @jakarta.validation.constraints.NotNull UUID clientMessageId,
        @Schema(description = "Text message content to send into the selected conversation.", example = "Chào anh, em đã xem meeting link và sẽ tham gia đúng giờ.")
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
        String content,
        UUID replyToMessageId,
        java.util.List<UUID> attachmentIntentIds
) {
    /** Compatibility bridge for Java callers; HTTP clients must provide a stable clientMessageId. */
    public SendMessageRequest(String content) { this(UUID.randomUUID(), content, null, java.util.List.of()); }
}
