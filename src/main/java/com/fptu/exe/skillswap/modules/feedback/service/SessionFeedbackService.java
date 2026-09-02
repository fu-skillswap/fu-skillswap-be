package com.fptu.exe.skillswap.modules.feedback.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMapper;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingFeedbackPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.feedback.domain.SessionFeedback;
import com.fptu.exe.skillswap.modules.feedback.dto.response.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.dto.request.SubmitFeedbackRequest;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionFeedbackService implements BookingFeedbackPort {

    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final BookingQueryPort bookingQueryPort;
    private final MentorQueryPort mentorQueryPort;
    private final UserQueryPort userQueryPort;
    private final NotificationCommandPort notificationCommandPort;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public boolean hasSubmittedFeedback(UUID bookingId, UUID reviewerId) {
        if (bookingId == null || reviewerId == null) {
            return false;
        }
        return sessionFeedbackRepository.existsByBookingIdAndReviewerId(bookingId, reviewerId);
    }

    @Transactional
    public SessionFeedbackResponse submitFeedback(UUID reviewerId, UUID bookingId, SubmitFeedbackRequest request) {
        if (reviewerId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        Booking booking = bookingQueryPort.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi học"));

        BookingCompletionOutcome outcome = BookingStateMapper.toCanonicalCompletionOutcome(booking);
        if (booking.getStatus() != BookingStatus.COMPLETED
                || (outcome != BookingCompletionOutcome.USER_CONFIRMED && outcome != BookingCompletionOutcome.AUTO_CLOSED)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể gửi đánh giá cho buổi học đã hoàn thành");
        }

        UUID menteeUserId = booking.getMenteeUserId();
        UUID mentorUserId = booking.getMentorUserId();
        if (mentorUserId == null || (userQueryPort != null && !userQueryPort.existsById(mentorUserId))) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin mentor");
        }

        if (!reviewerId.equals(menteeUserId)) {
            if (reviewerId.equals(mentorUserId)) {
                throw new BaseException(ErrorCode.ACCESS_DENIED, "Chỉ Mentee mới được quyền đánh giá Mentor");
            } else {
                throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không tham gia buổi học này để gửi đánh giá");
            }
        }

        User reviewer = entityManager.getReference(User.class, menteeUserId);
        User reviewee = entityManager.getReference(User.class, mentorUserId);
        UserSummaryRecord reviewerSummary = userQueryPort.findUserSummaryById(menteeUserId).orElse(null);

        if (sessionFeedbackRepository.existsByBookingIdAndReviewerId(bookingId, reviewerId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã gửi đánh giá cho buổi học này rồi");
        }

        SessionFeedback feedback = SessionFeedback.builder()
                .booking(booking)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(request.getRating())
                .satisfactionLevel(request.getSatisfactionLevel())
                .comment(request.getComment())
                .wouldRecommend(request.getWouldRecommend())
                .isPublic(request.getIsPublic() == null ? true : request.getIsPublic())
                .build();

        feedback = sessionFeedbackRepository.saveAndFlush(feedback);

        if (reviewee.getId().equals(mentorUserId)) {
            updateMentorRatingStats(mentorUserId, request.getRating());
        }

        notificationCommandPort.publish(new NotificationCommandPort.NotificationIntent(
                reviewee.getId(),
                "FEEDBACK_RECEIVED",
                "Bạn vừa nhận được đánh giá mới",
                (reviewerSummary == null ? "Mentee" : reviewerSummary.fullName()) + " đã gửi đánh giá sau buổi mentoring.",
                "BOOKING",
                booking.getId(),
                "/bookings/" + booking.getId()
        ));

        return toResponse(feedback);
    }

    @Transactional(readOnly = true)
    public SessionFeedbackResponse getBookingFeedback(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = bookingQueryPort.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi học"));

        UUID menteeId = booking.getMenteeUserId();
        UUID mentorId = booking.getMentorUserId();

        if (!currentUserId.equals(menteeId) && !currentUserId.equals(mentorId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đánh giá của buổi học này");
        }

        return sessionFeedbackRepository.findByBookingId(bookingId)
                .map(this::toResponse)
                .orElse(null);
    }

    private void updateMentorRatingStats(UUID mentorUserId, int newRating) {
        MentorProfile lockedProfile = mentorQueryPort.findMentorProfileByIdForUpdate(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
        entityManager.refresh(lockedProfile);
        
        int currentCount = lockedProfile.getTotalReviews() == null ? 0 : lockedProfile.getTotalReviews();
        BigDecimal currentAvg = lockedProfile.getAverageRating() == null ? BigDecimal.ZERO : lockedProfile.getAverageRating();
        
        // O(1) Incremental Update: NewAvg = ((OldAvg * OldCount) + NewRating) / (OldCount + 1)
        BigDecimal newSum = currentAvg.multiply(BigDecimal.valueOf(currentCount)).add(BigDecimal.valueOf(newRating));
        BigDecimal newAvg = newSum.divide(BigDecimal.valueOf(currentCount + 1), 2, RoundingMode.HALF_UP);
        
        lockedProfile.setTotalReviews(currentCount + 1);
        lockedProfile.setAverageRating(newAvg);
        mentorQueryPort.saveMentorProfile(lockedProfile);
    }

    private SessionFeedbackResponse toResponse(SessionFeedback feedback) {
        return SessionFeedbackResponse.builder()
                .id(feedback.getId())
                .sessionId(feedback.getBooking().getId())
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
