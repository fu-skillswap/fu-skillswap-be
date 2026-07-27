package com.fptu.exe.skillswap.modules.conversation.dto.response;
import java.time.Instant;
public record ChatAttachmentDownloadResponse(String downloadUrl, Instant expiresAt) {}
