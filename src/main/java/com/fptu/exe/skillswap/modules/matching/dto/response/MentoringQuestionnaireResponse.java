package com.fptu.exe.skillswap.modules.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Bộ câu hỏi nhu cầu mentoring đang active")
public record MentoringQuestionnaireResponse(
        UUID activationId,
        UUID versionId,
        Integer versionNumber,
        String phase,
        LocalDateTime activatedAt,
        List<MentoringQuestionnaireQuestionResponse> questions
) {
}
