package com.fptu.exe.skillswap.modules.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Tóm tắt version câu hỏi nhu cầu mentoring")
public record AdminQuestionnaireVersionSummaryResponse(
        UUID id,
        Integer versionNumber,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
