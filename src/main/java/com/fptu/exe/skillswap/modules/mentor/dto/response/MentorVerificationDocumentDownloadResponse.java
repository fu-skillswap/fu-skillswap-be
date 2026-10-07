package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "URL tạm thời để admin xem tài liệu xác thực mentor")
public record MentorVerificationDocumentDownloadResponse(
        @Schema(description = "Signed URL ngắn hạn để xem file", example = "https://storage.example/private-document")
        String downloadUrl,
        @Schema(description = "Thời điểm URL hết hạn")
        OffsetDateTime expiresAt
) { }
