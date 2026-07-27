package com.fptu.exe.skillswap.modules.filestorage.dto.response;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record PublicAssetUploadIntentResponse(UUID uploadIntentId, String uploadUrl, LocalDateTime expiresAt, Map<String, String> requiredHeaders) {}
