package com.fptu.exe.skillswap.modules.feedback.port;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable public review projection consumed by mentor discovery. */
public record MentorReviewProjection(
        UUID reviewId, UUID reviewerUserId, String reviewerDisplayName, String reviewerAvatarUrl,
        Integer rating, String comment, LocalDateTime createdAt
) {
}
