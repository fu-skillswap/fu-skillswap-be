package com.fptu.exe.skillswap.modules.mentor.dto;

import lombok.Builder;

@Builder
public record MentorVerificationAllowedActionsResponse(
        boolean canUploadDocuments,
        boolean canSubmit,
        boolean canWithdraw
) {
}
