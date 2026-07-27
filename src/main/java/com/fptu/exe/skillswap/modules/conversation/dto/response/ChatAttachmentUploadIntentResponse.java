package com.fptu.exe.skillswap.modules.conversation.dto.response;
import java.time.Instant; import java.util.UUID;
public record ChatAttachmentUploadIntentResponse(UUID uploadIntentId, String uploadUrl, Instant expiresAt, String requiredContentType) {}
