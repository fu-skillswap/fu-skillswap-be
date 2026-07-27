package com.fptu.exe.skillswap.modules.conversation.dto.request;
import jakarta.validation.constraints.*;
public record ChatAttachmentUploadIntentRequest(
 @NotBlank @Size(max=255) String filename,
 @NotBlank @Size(max=150) String contentType,
 @Positive long sizeBytes) {}
