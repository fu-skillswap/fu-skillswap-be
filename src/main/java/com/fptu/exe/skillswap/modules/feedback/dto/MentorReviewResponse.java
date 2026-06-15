package com.fptu.exe.skillswap.modules.feedback.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MentorReviewResponse(
        UUID reviewId,
        UUID reviewerUserId,
        String reviewerDisplayName,
        String reviewerAvatarUrl,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
}
