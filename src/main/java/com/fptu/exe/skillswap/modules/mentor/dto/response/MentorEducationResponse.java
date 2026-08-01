package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Academic context used as peer-mentor evidence. The object is always present; fields may be null.")
public record MentorEducationResponse(
        UUID campusId,
        String campusName,
        UUID programId,
        String programName,
        UUID specializationId,
        String specializationName,
        Integer semester,
        Boolean alumni
) {
}
