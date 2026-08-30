package com.fptu.exe.skillswap.modules.feedback.service;

import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.port.FeedbackQueryPort;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackQueryPortImpl implements FeedbackQueryPort {

    private final SessionFeedbackRepository sessionFeedbackRepository;

    @Override
    public List<MentorReviewResponse> findRecentPublicReviewsByMentorUserId(UUID mentorUserId, int limit) {
        if (mentorUserId == null) {
            return Collections.emptyList();
        }
        int max = Math.max(1, Math.min(limit, 50));
        Page<MentorReviewQueryRow> rowPage = sessionFeedbackRepository.findPublicMentorReviews(mentorUserId, PageRequest.of(0, max));
        return rowPage.getContent().stream()
                .map(row -> new MentorReviewResponse(
                        row.reviewId(),
                        row.reviewerUserId(),
                        row.reviewerDisplayName(),
                        row.reviewerAvatarUrl(),
                        row.rating(),
                        row.comment(),
                        row.createdAt()
                ))
                .toList();
    }

    @Override
    public PageResponse<MentorReviewResponse> getMentorReviews(UUID mentorUserId, BasePageRequest pageRequest) {
        BasePageRequest safeRequest = pageRequest == null ? new BasePageRequest() : pageRequest;
        int page = Math.max(safeRequest.getPage(), 0);
        int size = Math.min(Math.max(safeRequest.getSize(), 1), 20);
        Sort.Direction direction = safeRequest.resolveDirection();
        String sortBy = safeRequest.getSortBy() == null ? "createdAt" : safeRequest.getSortBy().trim();

        List<Sort.Order> orders = switch (sortBy) {
            case "rating" -> List.of(
                    new Sort.Order(direction, "rating"),
                    new Sort.Order(Sort.Direction.DESC, "createdAt")
            );
            default -> List.of(
                    new Sort.Order(direction, "createdAt"),
                    new Sort.Order(Sort.Direction.DESC, "rating")
            );
        };
        Page<MentorReviewQueryRow> rowPage = sessionFeedbackRepository.findPublicMentorReviews(
                mentorUserId,
                PageRequest.of(page, size, Sort.by(orders))
        );

        return PageResponse.<MentorReviewResponse>builder()
                .content(rowPage.getContent().stream().map(row -> new MentorReviewResponse(
                        row.reviewId(),
                        row.reviewerUserId(),
                        row.reviewerDisplayName(),
                        row.reviewerAvatarUrl(),
                        row.rating(),
                        row.comment(),
                        row.createdAt()
                )).toList())
                .page(rowPage.getNumber())
                .size(rowPage.getSize())
                .totalElements(rowPage.getTotalElements())
                .totalPages(rowPage.getTotalPages())
                .last(rowPage.isLast())
                .build();
    }
}
