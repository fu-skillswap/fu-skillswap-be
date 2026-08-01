package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Peer-mentoring support levels. Values are 1-4 when the mentor has configured them.")
public record MentorSupportLevelsResponse(
        Integer foundation,
        Integer outputReview,
        Integer direction
) {
}
