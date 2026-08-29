package com.fptu.exe.skillswap.modules.course.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CoursePdfUploadInitResponse(UUID materialId, String uploadUrl, Instant expiresAt, String requiredContentType) {
}
