package com.fptu.exe.skillswap.modules.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Dữ liệu gửi tin nhắn vào cuộc trò chuyện mà tài khoản hiện tại tham gia. Backend tự xác định người gửi từ JWT.")
public record SendMessageRequest(
        @jakarta.validation.constraints.NotNull
        @Schema(description = "ID do FE tạo ổn định cho lần gửi này để tránh tạo trùng tin nhắn khi request bị retry.", example = "019f7234-aaaa-bbbb-cccc-1234567890ab", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID clientMessageId,
        @Schema(description = "Nội dung tin nhắn; có thể để trống khi chỉ gửi file đính kèm.", example = "Chào anh, em đã xem meeting link và sẽ tham gia đúng giờ.", nullable = true)
        @Size(max = 2000, message = "Nội dung tin nhắn không được vượt quá 2000 ký tự")
        String content,
        @Schema(description = "ID tin nhắn được trả lời, nếu có. Bỏ qua hoặc để null khi gửi tin nhắn mới.", nullable = true)
        UUID replyToMessageId,
        @Schema(description = "Danh sách ID upload intent đã được tạo trước đó. Có thể để trống nếu không gửi file.", nullable = true)
        java.util.List<UUID> attachmentIntentIds
) {
    /** Compatibility bridge for Java callers; HTTP clients must provide a stable clientMessageId. */
    public SendMessageRequest(String content) { this(UUID.randomUUID(), content, null, java.util.List.of()); }
}
