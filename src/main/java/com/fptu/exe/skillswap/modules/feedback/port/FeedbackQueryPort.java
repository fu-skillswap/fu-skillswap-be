package com.fptu.exe.skillswap.modules.feedback.port;

import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface FeedbackQueryPort {
    List<MentorReviewResponse> findRecentPublicReviewsByMentorUserId(UUID mentorUserId, int limit);
    PageResponse<MentorReviewResponse> getMentorReviews(UUID mentorUserId, BasePageRequest pageRequest);
}
