package com.fptu.exe.skillswap.modules.chat.dto.response;
import java.time.Instant;
public record ChatAttachmentDownloadResponse(String downloadUrl, Instant expiresAt) {}
