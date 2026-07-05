package com.fptu.exe.skillswap.modules.matching.dto.response;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Câu hỏi nhu cầu mentoring")
public record MentoringQuestionnaireQuestionResponse(
        String code,
        MentoringQuestionType type,
        String questionText,
        Integer displayOrder,
        List<MentoringQuestionnaireOptionResponse> options
) {
}
