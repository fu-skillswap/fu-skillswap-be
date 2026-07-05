package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Các option cấu hình mentor profile")
public record MentorProfileOptionsResponse(
        List<MentorSupportLevelOptionResponse> foundationSupportLevels,
        List<MentorSupportLevelOptionResponse> outputReviewSupportLevels,
        List<MentorSupportLevelOptionResponse> directionSupportLevels
) {
}
