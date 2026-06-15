package com.fptu.exe.skillswap.modules.feedback.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.feedback.domain.SessionFeedback;
import com.fptu.exe.skillswap.modules.feedback.dto.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.dto.SubmitFeedbackRequest;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionFeedbackService {

    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final SessionRepository sessionRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Transactional
    public SessionFeedbackResponse submitFeedback(UUID reviewerId, UUID sessionId, SubmitFeedbackRequest request) {
        if (reviewerId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi học"));

        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể gửi đánh giá cho buổi học đã hoàn thành");
        }

        Booking booking = session.getBooking();
        User mentee = booking.getMentee();
        MentorProfile mentorProfile = booking.getMentorProfile();
        User mentor = mentorProfile.getUser();

        if (!reviewerId.equals(mentee.getId())) {
            if (reviewerId.equals(mentor.getId())) {
                throw new BaseException(ErrorCode.ACCESS_DENIED, "Chỉ Mentee mới được quyền đánh giá Mentor");
            } else {
                throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không tham gia buổi học này để gửi đánh giá");
            }
        }

        User reviewer = mentee;
        User reviewee = mentor;

        if (sessionFeedbackRepository.existsBySessionIdAndReviewerId(sessionId, reviewerId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã gửi đánh giá cho buổi học này rồi");
        }

        SessionFeedback feedback = SessionFeedback.builder()
                .session(session)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(request.getRating())
                .satisfactionLevel(request.getSatisfactionLevel())
                .comment(request.getComment())
                .wouldRecommend(request.getWouldRecommend())
                .isPublic(request.getIsPublic() == null ? true : request.getIsPublic())
                .build();

        feedback = sessionFeedbackRepository.saveAndFlush(feedback);

        // If the reviewee is a Mentor, recalculate and update their MentorProfile stats
        if (reviewee.getId().equals(mentor.getId())) {
            updateMentorRatingStats(mentorProfile);
        }

        return toResponse(feedback);
    }

    private void updateMentorRatingStats(MentorProfile mentorProfile) {
        UUID mentorUserId = mentorProfile.getUserId();
        long count = sessionFeedbackRepository.countFeedbacksByRevieweeId(mentorUserId);
        Double avg = sessionFeedbackRepository.getAverageRatingByRevieweeId(mentorUserId);

        mentorProfile.setTotalReviews((int) count);
        mentorProfile.setAverageRating(avg == null ? BigDecimal.ZERO : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        mentorProfileRepository.save(mentorProfile);
    }

    private SessionFeedbackResponse toResponse(SessionFeedback feedback) {
        return SessionFeedbackResponse.builder()
                .id(feedback.getId())
                .sessionId(feedback.getSession().getId())
                .reviewerUserId(feedback.getReviewer().getId())
                .reviewerDisplayName(feedback.getReviewer().getFullName())
                .revieweeUserId(feedback.getReviewee().getId())
                .revieweeDisplayName(feedback.getReviewee().getFullName())
                .rating(feedback.getRating())
                .satisfactionLevel(feedback.getSatisfactionLevel())
                .comment(feedback.getComment())
                .wouldRecommend(feedback.getWouldRecommend())
                .isPublic(feedback.isPublic())
                .createdAt(feedback.getCreatedAt())
                .build();
    }
}
