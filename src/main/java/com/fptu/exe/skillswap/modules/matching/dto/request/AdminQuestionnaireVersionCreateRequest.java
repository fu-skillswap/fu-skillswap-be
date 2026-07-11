package com.fptu.exe.skillswap.modules.matching.dto.request;

import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Tạo version mới cho bộ câu hỏi nhu cầu mentoring. Nếu questions rỗng, backend dùng bộ mặc định.")
public record AdminQuestionnaireVersionCreateRequest(
        List<@Valid QuestionRequest> questions
) {
    public record QuestionRequest(
            @NotBlank String code,
            @NotNull MentoringQuestionType type,
            @NotBlank String questionText,
            @NotEmpty List<@Valid OptionRequest> options
    ) {
    }

    public record OptionRequest(
            @NotBlank String code,
            @NotBlank String label,
            Integer scoreValue
    ) {
    }
}
