package com.fptu.exe.skillswap.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Danh mục tùy chọn hồ sơ mentor")
public record MentorProfileOptionsResponse(
        @Schema(description = "Tùy chọn mức độ hỗ trợ lấy gốc")
        List<MentorSupportLevelOptionResponse> foundationSupportLevels,

        @Schema(description = "Tùy chọn mức độ hỗ trợ review bài nộp/project")
        List<MentorSupportLevelOptionResponse> outputReviewSupportLevels,

        @Schema(description = "Tùy chọn mức độ hỗ trợ định hướng/OJT/career")
        List<MentorSupportLevelOptionResponse> directionSupportLevels
) {}
