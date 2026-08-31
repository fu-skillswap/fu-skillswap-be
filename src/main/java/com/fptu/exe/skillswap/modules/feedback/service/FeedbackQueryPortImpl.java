package com.fptu.exe.skillswap.modules.feedback.service;

import com.fptu.exe.skillswap.modules.feedback.port.FeedbackQueryPort;
import com.fptu.exe.skillswap.modules.feedback.port.MentorReviewProjection;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackQueryPortImpl implements FeedbackQueryPort {

    private final SessionFeedbackRepository sessionFeedbackRepository;

    @Override
    public PageResponse<MentorReviewProjection> findPublicMentorReviews(UUID mentorUserId, int page, int size) {
        Page<MentorReviewQueryRow> result = sessionFeedbackRepository.findPublicMentorReviews(
                mentorUserId, org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size)));
        return PageResponse.<MentorReviewProjection>builder()
                .content(result.getContent().stream().map(row -> new MentorReviewProjection(row.reviewId(), row.reviewerUserId(),
                        row.reviewerDisplayName(), row.reviewerAvatarUrl(), row.rating(), row.comment(), row.createdAt())).toList())
                .page(result.getNumber()).size(result.getSize()).totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()).last(result.isLast()).build();
    }
}
