package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "What the mentor can help with before a mentee chooses a service.")
public record MentorMentoringResponse(
        String bio,
        String expertiseDescription,
        List<MentorTagResponse> helpTopics,
        MentorSupportLevelsResponse supportLevels
) {
}
