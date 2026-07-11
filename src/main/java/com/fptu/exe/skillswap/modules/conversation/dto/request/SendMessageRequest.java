package com.fptu.exe.skillswap.modules.conversation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for sending a text message into an existing conversation that the current user participates in.")
public record SendMessageRequest(
        @Schema(description = "Text message content to send into the selected conversation.", example = "Chào anh, em đã xem meeting link và sẽ tham gia đúng giờ.")
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
        String content
) {
}
