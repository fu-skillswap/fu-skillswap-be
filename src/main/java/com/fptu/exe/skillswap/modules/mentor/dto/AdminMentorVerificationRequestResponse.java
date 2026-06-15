package com.fptu.exe.skillswap.modules.mentor.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminMentorVerificationRequestResponse(
        UUID requestId,
        UUID mentorUserId,
        String mentorEmail,
        String mentorFullName,
        String mentorAvatarUrl,
        VerificationStatus status,
        String submitNote,
        String reviewNote,
        String rejectionReason,
        Integer revisionCount,
        String reviewerEmail,
        String lockedByAdminEmail,
        LocalDateTime lockedAt,
        LocalDateTime lockExpiresAt,
        boolean canReview,
        LocalDateTime submittedAt,
        LocalDateTime termsAcceptedAt,
        String termsVersion,
        LocalDateTime reviewedAt,
        LocalDateTime approvedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MentorVerificationDocumentResponse> documents,
        List<MentorVerificationTimelineEventResponse> timeline,
        MentorVerificationChecklistResponse checklist
) {
}
