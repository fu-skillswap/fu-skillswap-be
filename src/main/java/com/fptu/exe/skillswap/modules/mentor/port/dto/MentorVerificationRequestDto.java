package com.fptu.exe.skillswap.modules.mentor.port.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationChecklistResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.StudentProfileResponse;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MentorVerificationRequestDto(
        UUID requestId,
        UUID mentorUserId,
        String mentorFullName,
        String mentorEmail,
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
        MentorVerificationChecklistResponse checklist,
        MentorProfileResponse mentorProfile,
        StudentProfileResponse studentProfile
) {}
