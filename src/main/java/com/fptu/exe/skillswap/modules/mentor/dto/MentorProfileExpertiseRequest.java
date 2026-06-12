package com.fptu.exe.skillswap.modules.mentor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Step 2 - Chuyên môn, kinh nghiệm và chủ đề mentor có thể hỗ trợ")
public record MentorProfileExpertiseRequest(
        @Schema(description = "Danh sách tag chuyên môn đã được duyệt", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Danh sách chuyên môn không được để trống")
        @Size(max = 20, message = "Không được chọn quá 20 tag chuyên môn")
        List<@NotNull(message = "Tag chuyên môn không hợp lệ") UUID> expertiseTagIds,

        @Schema(description = "Danh sách help topic mentor có thể hỗ trợ", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Danh sách chủ đề hỗ trợ không được để trống")
        @Size(max = 20, message = "Không được chọn quá 20 chủ đề hỗ trợ")
        List<@NotNull(message = "Chủ đề hỗ trợ không hợp lệ") UUID> helpTopicIds,

        @Schema(example = "2.5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Số năm kinh nghiệm không được để trống")
        @DecimalMin(value = "0.0", message = "Số năm kinh nghiệm không được âm")
        @DecimalMax(value = "60.0", message = "Số năm kinh nghiệm không được quá 60")
        BigDecimal yearsOfExperience,

        @Schema(example = "Software Engineering", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Ngành nghề không được để trống")
        @Size(max = 120, message = "Ngành nghề không được quá 120 ký tự")
        String industry,

        @Schema(example = "Tập trung backend Java, API design và performance tuning.")
        @Size(max = 3000, message = "Tóm tắt chuyên môn không được quá 3000 ký tự")
        String expertiseSummary,

        @Schema(example = "https://www.linkedin.com/in/example")
        String linkedinUrl,

        @Schema(example = "https://github.com/example")
        String githubUrl,

        @Schema(example = "https://example.dev")
        String portfolioUrl
) {
}
