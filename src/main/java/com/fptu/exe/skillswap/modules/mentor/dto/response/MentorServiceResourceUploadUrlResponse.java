package com.fptu.exe.skillswap.modules.mentor.dto.response;
import java.time.Instant;
import java.util.UUID;
public record MentorServiceResourceUploadUrlResponse(UUID uploadIntentId, String uploadUrl, Instant expiresAt, String requiredContentType) {}
