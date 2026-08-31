package com.fptu.exe.skillswap.modules.feedback.port;

import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

public interface FeedbackQueryPort {

    PageResponse<MentorReviewProjection> findPublicMentorReviews(UUID mentorUserId, int page, int size);
}
