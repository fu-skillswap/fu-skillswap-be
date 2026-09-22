package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canonical public education summary. Fields incompatible with the profile type are null.")
public record PublicMentorEducationResponse(
        @Schema(description = "Canonical profile type; legacy profiles do not have an education object.")
        StudentProfileType type,
        @Schema(nullable = true) String schoolName,
        @Schema(nullable = true) PublicEducationReferenceResponse province,
        @Schema(nullable = true) PublicEducationReferenceResponse institution,
        @Schema(nullable = true) PublicEducationReferenceResponse fieldGroup,
        @Schema(nullable = true) String major
) {
}
