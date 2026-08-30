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
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.feedback.event.SessionFeedbackSubmittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
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
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationService notificationService;

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

        // Lock the booking and associated mentor profile early to establish lock order and avoid deadlock
        Booking booking = bookingQueryPort.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi học"));

        BookingCompletionOutcome outcome = BookingStateMapper.toCanonicalCompletionOutcome(booking);
        if (booking.getStatus() != BookingStatus.COMPLETED
                || (outcome != BookingCompletionOutcome.USER_CONFIRMED && outcome != BookingCompletionOutcome.AUTO_CLOSED)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể gửi đánh giá cho buổi học đã hoàn thành");
        }

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

        // If the reviewee is a Mentor, recalculate and update their MentorProfile stats
        if (applicationEventPublisher != null && reviewee.getId().equals(mentor.getId())) {
            applicationEventPublisher.publishEvent(new SessionFeedbackSubmittedEvent(mentorProfile.getUserId(), request.getRating()));
        }

        notificationService.createNotification(
                reviewee.getId(),
                NotificationType.FEEDBACK_RECEIVED,
                "Bạn vừa nhận được đánh giá mới",
                reviewer.getFullName() + " đã gửi đánh giá sau buổi mentoring.",
                "BOOKING",
                booking.getId(),
                "/bookings/" + booking.getId()
        );

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

        UUID menteeId = booking.getMentee() != null ? booking.getMentee().getId() : null;
        UUID mentorId = (booking.getMentorProfile() != null && booking.getMentorProfile().getUser() != null)
                ? booking.getMentorProfile().getUser().getId() : null;

        if (!currentUserId.equals(menteeId) && !currentUserId.equals(mentorId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đánh giá của buổi học này");
        }

        return sessionFeedbackRepository.findByBookingId(bookingId)
                .map(this::toResponse)
                .orElse(null);
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
