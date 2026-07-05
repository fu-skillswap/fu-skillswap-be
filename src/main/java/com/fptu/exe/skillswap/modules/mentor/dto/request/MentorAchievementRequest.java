package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Học vấn hoặc giải thưởng nổi bật của mentor")
public record MentorAchievementRequest(
        @Schema(example = "Top 10 Hackathon FPTU")
        @NotBlank(message = "Tiêu đề học vấn/giải thưởng không được để trống")
        @Size(max = 200, message = "Tiêu đề không được quá 200 ký tự")
        String title,

        @Schema(example = "Mô tả ngắn giải thưởng hoặc thành tích")
        @Size(max = 2000, message = "Mô tả giải thưởng không được quá 2000 ký tự")
        String awardDescription,

        @Schema(example = "2026-03-01")
        LocalDate achievedAt,

        @Schema(example = "Case study: Growth campaign")
        @Size(max = 200, message = "Product header không được quá 200 ký tự")
        String productHeader,

        @Schema(example = "Mô tả sản phẩm/case đi kèm thành tích")
        @Size(max = 2000, message = "Product description không được quá 2000 ký tự")
        String productDescription,

        @Schema(example = "https://demo.example.com")
        String demoUrl
) {
}
