package com.fptu.exe.skillswap.modules.feedback.repository.query;

import java.time.LocalDateTime;
import java.util.UUID;

public record MentorReviewQueryRow(
        UUID reviewId,
        UUID reviewerUserId,
        String reviewerDisplayName,
        String reviewerAvatarUrl,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
}
