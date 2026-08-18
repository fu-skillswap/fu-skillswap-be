package com.fptu.exe.skillswap.modules.mentor.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Trạng thái wizard do backend tính; frontend không tự suy diễn điều kiện xác thực. */
public record MentorVerificationProgressResponse(
        UUID requestId,
        String applicationStatus,
        LocalDateTime submittedAt,
        LocalDateTime estimatedReviewBy,
        Integer reviewTargetHours,
        boolean reviewOverdue,
        List<Step> submissionSteps,
        List<Step> activationSteps,
        NextAction nextAction
) {
    public MentorVerificationProgressResponse {
        submissionSteps = submissionSteps == null ? List.of() : List.copyOf(submissionSteps);
        activationSteps = activationSteps == null ? List.of() : List.copyOf(activationSteps);
    }

    public record Step(String code, boolean completed, boolean requiredForSubmission,
                       boolean requiredForBookingOffer, String actionPath, String message) {
    }

    public record NextAction(String code, String actionPath, String message) {
    }
}
