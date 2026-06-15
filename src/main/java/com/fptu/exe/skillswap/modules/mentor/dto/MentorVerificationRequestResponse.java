package com.fptu.exe.skillswap.modules.mentor.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MentorVerificationRequestResponse(
        UUID requestId,
        UUID mentorUserId,
        VerificationStatus status,
        String submitNote,
        String reviewNote,
        String rejectionReason,
        Integer revisionCount,
        LocalDateTime submittedAt,
        LocalDateTime termsAcceptedAt,
        String termsVersion,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MentorVerificationDocumentResponse> documents,
        List<MentorVerificationTimelineEventResponse> timeline,
        MentorVerificationChecklistResponse checklist,
        MentorVerificationAllowedActionsResponse allowedActions
) {
}
