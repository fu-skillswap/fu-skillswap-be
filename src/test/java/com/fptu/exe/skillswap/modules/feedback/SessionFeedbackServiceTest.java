package com.fptu.exe.skillswap.modules.feedback;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.feedback.domain.SessionFeedback;
import com.fptu.exe.skillswap.modules.feedback.dto.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.dto.SubmitFeedbackRequest;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.service.SessionFeedbackService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionFeedbackServiceTest {

    @Mock
    private SessionFeedbackRepository sessionFeedbackRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @InjectMocks
    private SessionFeedbackService sessionFeedbackService;

    @Test
    void submitFeedback_menteeToMentor_shouldSaveFeedbackAndRecalculateStats() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User mentee = User.builder().id(menteeId).fullName("Mentee User").build();
        User mentor = User.builder().id(mentorId).fullName("Mentor User").build();
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .user(mentor)
                .averageRating(BigDecimal.valueOf(5.0))
                .totalReviews(0)
                .build();

        Booking booking = Booking.builder()
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .build();

        Session session = Session.builder()
                .id(sessionId)
                .status(SessionStatus.COMPLETED)
                .booking(booking)
                .build();

        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .rating(5)
                .satisfactionLevel(5)
                .comment("Excellent session!")
                .wouldRecommend(true)
                .isPublic(true)
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionFeedbackRepository.existsBySessionIdAndReviewerId(sessionId, menteeId)).thenReturn(false);
        when(sessionFeedbackRepository.saveAndFlush(any(SessionFeedback.class))).thenAnswer(invocation -> {
            SessionFeedback fb = invocation.getArgument(0);
            fb.setId(UUID.randomUUID());
            return fb;
        });

        when(sessionFeedbackRepository.countFeedbacksByRevieweeId(mentorId)).thenReturn(1L);
        when(sessionFeedbackRepository.getAverageRatingByRevieweeId(mentorId)).thenReturn(5.0);

        SessionFeedbackResponse response = sessionFeedbackService.submitFeedback(menteeId, sessionId, request);

        assertNotNull(response);
        assertEquals(menteeId, response.getReviewerUserId());
        assertEquals(mentorId, response.getRevieweeUserId());
        assertEquals(5, response.getRating());

        verify(sessionFeedbackRepository).saveAndFlush(any(SessionFeedback.class));
        verify(mentorProfileRepository).save(mentorProfile);
        assertEquals(1, mentorProfile.getTotalReviews());
        assertEquals(new BigDecimal("5.00"), mentorProfile.getAverageRating());
    }

    @Test
    void submitFeedback_mentorToMentee_shouldThrowAccessDenied() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User mentee = User.builder().id(menteeId).fullName("Mentee User").build();
        User mentor = User.builder().id(mentorId).fullName("Mentor User").build();
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .user(mentor)
                .build();

        Booking booking = Booking.builder()
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .build();

        Session session = Session.builder()
                .id(sessionId)
                .status(SessionStatus.COMPLETED)
                .booking(booking)
                .build();

        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder()
                .rating(4)
                .satisfactionLevel(4)
                .comment("Good learner")
                .wouldRecommend(true)
                .isPublic(true)
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(mentorId, sessionId, request)
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(sessionFeedbackRepository, never()).saveAndFlush(any(SessionFeedback.class));
    }

    @Test
    void submitFeedback_sessionNotCompleted_shouldThrowResourceConflict() {
        UUID sessionId = UUID.randomUUID();
        Session session = Session.builder()
                .id(sessionId)
                .status(SessionStatus.SCHEDULED)
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder().rating(5).build();

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(UUID.randomUUID(), sessionId, request)
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        assertEquals("Chỉ có thể gửi đánh giá cho buổi học đã hoàn thành", exception.getMessage());
    }

    @Test
    void submitFeedback_duplicateFeedback_shouldThrowConflict() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User mentee = User.builder().id(menteeId).build();
        User mentor = User.builder().id(mentorId).build();
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentorId).user(mentor).build();
        Booking booking = Booking.builder().mentee(mentee).mentorProfile(mentorProfile).build();
        Session session = Session.builder().id(sessionId).status(SessionStatus.COMPLETED).booking(booking).build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionFeedbackRepository.existsBySessionIdAndReviewerId(sessionId, menteeId)).thenReturn(true);

        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder().rating(5).build();

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(menteeId, sessionId, request)
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void submitFeedback_nonParticipant_shouldThrowAccessDenied() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID nonParticipantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        User mentee = User.builder().id(menteeId).build();
        User mentor = User.builder().id(mentorId).build();
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentorId).user(mentor).build();
        Booking booking = Booking.builder().mentee(mentee).mentorProfile(mentorProfile).build();
        Session session = Session.builder().id(sessionId).status(SessionStatus.COMPLETED).booking(booking).build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SubmitFeedbackRequest request = SubmitFeedbackRequest.builder().rating(5).build();

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(nonParticipantId, sessionId, request)
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }
}
