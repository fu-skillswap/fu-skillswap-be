package com.fptu.exe.skillswap.modules.course.dto.response;

import java.time.Instant;

public record CourseMaterialDownloadResponse(String downloadUrl, Instant expiresAt) {
}
