package com.fptu.exe.skillswap.modules.chat.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
@Schema(description = "Thông tin file FE muốn upload vào cuộc trò chuyện. Backend kiểm tra loại file và kích thước trước khi cấp upload URL.")
public record ChatAttachmentUploadIntentRequest(
 @Schema(description = "Tên file hiển thị; không gửi đường dẫn local.", example = "lesson-notes.pdf", requiredMode = Schema.RequiredMode.REQUIRED)
 @NotBlank @Size(max=255) String filename,
 @Schema(description = "MIME type của file, ví dụ application/pdf hoặc image/jpeg.", example = "application/pdf", requiredMode = Schema.RequiredMode.REQUIRED)
 @NotBlank @Size(max=150) String contentType,
 @Schema(description = "Kích thước file tính bằng byte.", example = "245760", requiredMode = Schema.RequiredMode.REQUIRED)
 @Positive long sizeBytes) {}
