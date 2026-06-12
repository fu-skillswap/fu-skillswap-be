package com.fptu.exe.skillswap.modules.mentor.dto;

import lombok.Builder;

@Builder
public record MentorVerificationChecklistResponse(
        boolean academicProfileCompleted,
        boolean hasAffiliationProof,
        boolean hasExpertiseProof,
        boolean canSubmit
) {
}
