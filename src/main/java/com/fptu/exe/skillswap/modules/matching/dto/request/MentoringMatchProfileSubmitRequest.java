package com.fptu.exe.skillswap.modules.matching.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Submit 5 câu trả lời nhu cầu mentoring dạng flat để FE sửa ít")
public record MentoringMatchProfileSubmitRequest(
        @Schema(description = "Giữ field phase cho tương thích FE; backend không dùng để tách bộ câu hỏi", example = "ACTIVE")
        String phase,

        @NotBlank(message = "Câu 1 chưa có câu trả lời")
        String question1AnswerCode,

        @NotBlank(message = "Câu 2 chưa có câu trả lời")
        String question2AnswerCode,

        @NotBlank(message = "Câu 3 chưa có câu trả lời")
        String question3AnswerCode,

        @NotBlank(message = "Câu 4 chưa có câu trả lời")
        String question4AnswerCode,

        @NotBlank(message = "Câu 5 chưa có câu trả lời")
        String question5AnswerCode
) {
}
