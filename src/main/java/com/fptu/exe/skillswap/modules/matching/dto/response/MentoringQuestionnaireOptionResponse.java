package com.fptu.exe.skillswap.modules.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Option của câu hỏi nhu cầu mentoring")
public record MentoringQuestionnaireOptionResponse(
        String code,
        String label,
        Integer scoreValue,
        Integer displayOrder
) {
}
