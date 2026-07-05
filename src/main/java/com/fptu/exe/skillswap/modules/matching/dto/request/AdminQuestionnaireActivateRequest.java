package com.fptu.exe.skillswap.modules.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Kích hoạt một version câu hỏi nhu cầu mentoring")
public record AdminQuestionnaireActivateRequest(
        @NotNull(message = "versionId không được để trống")
        UUID versionId
) {
}
