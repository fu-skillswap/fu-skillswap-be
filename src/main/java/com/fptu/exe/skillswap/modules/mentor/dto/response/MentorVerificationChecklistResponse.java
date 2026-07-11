package com.fptu.exe.skillswap.modules.mentor.dto.response;

import lombok.Builder;

@Builder
public record MentorVerificationChecklistResponse(
        boolean academicProfileCompleted,
        boolean mentorProfileCompleted,
        boolean hasAffiliationProof,
        boolean hasExpertiseProof,
        boolean canSubmit
) {
}
