package com.fptu.exe.skillswap.modules.feedback.support;

import java.time.Instant;
import java.util.UUID;

/** Test fixture providing standardized session feedback snapshots and review models. */
public final class FeedbackTestFixture {

    private FeedbackTestFixture() {}

    public static UUID randomReviewId() {
        return UUID.randomUUID();
    }

    public static ReviewSnapshot createSampleReviewSnapshot(UUID mentorId, UUID reviewerId) {
        return new ReviewSnapshot(
                UUID.randomUUID(),
                mentorId != null ? mentorId : UUID.randomUUID(),
                reviewerId != null ? reviewerId : UUID.randomUUID(),
                5,
                "Excellent session, very clear explanations and great advice!",
                Instant.now()
        );
    }

    public record ReviewSnapshot(
            UUID reviewId,
            UUID mentorUserId,
            UUID reviewerUserId,
            int rating,
            String comment,
            Instant createdAt
    ) {}
}
