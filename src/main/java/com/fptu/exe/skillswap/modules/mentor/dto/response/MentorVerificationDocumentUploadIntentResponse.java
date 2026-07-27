package com.fptu.exe.skillswap.modules.mentor.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MentorVerificationDocumentUploadIntentResponse(
        UUID uploadIntentId, String uploadUrl, Instant expiresAt, Map<String, String> requiredHeaders
) {}
