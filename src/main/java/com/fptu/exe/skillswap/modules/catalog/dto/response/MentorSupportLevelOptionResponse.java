package com.fptu.exe.skillswap.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Tùy chọn mức độ hỗ trợ của mentor")
public record MentorSupportLevelOptionResponse(
        @Schema(description = "Giá trị mức hỗ trợ (1-4)", example = "1")
        Integer value,
        @Schema(description = "Nhãn mô tả mức hỗ trợ", example = "Gợi ý nhanh để mentee tự ôn lại")
        String label
) {}
