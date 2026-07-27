package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.*;

public record MentorVerificationDocumentUploadIntentRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 100) String contentType,
        @NotNull @Positive @Max(15 * 1024 * 1024) Long sizeBytes
) {}
