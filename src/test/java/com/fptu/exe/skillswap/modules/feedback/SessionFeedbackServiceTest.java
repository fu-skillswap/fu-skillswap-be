package com.fptu.exe.skillswap.modules.feedback;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.feedback.domain.SessionFeedback;
import com.fptu.exe.skillswap.modules.feedback.dto.request.SubmitFeedbackRequest;
import com.fptu.exe.skillswap.modules.feedback.dto.response.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.service.SessionFeedbackService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionFeedbackServiceTest {

    @Mock
    private SessionFeedbackRepository sessionFeedbackRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @InjectMocks
    private SessionFeedbackService sessionFeedbackService;

    private UUID menteeId;
    private UUID mentorId;
    private Booking booking;
    private MentorProfile mentorProfile;

    @BeforeEach
    void setUp() {
        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();

        User mentee = new User();
        mentee.setId(menteeId);
        mentee.setFullName("Mentee");

        User mentor = new User();
        mentor.setId(mentorId);
        mentor.setFullName("Mentor");

        mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorId);
        mentorProfile.setUser(mentor);

        booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setMentee(mentee);
        booking.setMentorProfile(mentorProfile);
    }

    @Test
    void submitFeedback_unauthenticated_shouldThrow() {
        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(null, UUID.randomUUID(), new SubmitFeedbackRequest())
        );

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void submitFeedback_bookingNotCompleted_shouldThrowConflict() {
        booking.setStatus(BookingStatus.ACCEPTED);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request())
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void submitFeedback_mentorReviewer_shouldThrowAccessDenied() {
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(mentorId, booking.getId(), request())
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void submitFeedback_duplicateReview_shouldThrowConflict() {
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(sessionFeedbackRepository.existsByBookingIdAndReviewerId(booking.getId(), menteeId)).thenReturn(true);

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request())
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void submitFeedback_success_shouldDefaultPublicAndUpdateMentorStats() {
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(sessionFeedbackRepository.existsByBookingIdAndReviewerId(booking.getId(), menteeId)).thenReturn(false);
        when(sessionFeedbackRepository.saveAndFlush(any(SessionFeedback.class))).thenAnswer(invocation -> {
            SessionFeedback feedback = invocation.getArgument(0);
            feedback.setId(UUID.randomUUID());
            return feedback;
        });
        when(sessionFeedbackRepository.countFeedbacksByRevieweeId(mentorId)).thenReturn(3L);
        when(sessionFeedbackRepository.getAverageRatingByRevieweeId(mentorId)).thenReturn(4.666);

        SubmitFeedbackRequest request = request();
        request.setIsPublic(null);

        SessionFeedbackResponse response = sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request);

        assertNotNull(response.getId());
        assertEquals(menteeId, response.getReviewerUserId());
        assertEquals(mentorId, response.getRevieweeUserId());
        assertEquals(3, mentorProfile.getTotalReviews());
        assertEquals("4.67", mentorProfile.getAverageRating().toString());
        assertEquals(true, response.isPublic());
        verify(mentorProfileRepository).save(mentorProfile);
    }

    @Test
    void submitFeedback_nonParticipant_shouldThrowAccessDenied() {
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(UUID.randomUUID(), booking.getId(), request())
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(sessionFeedbackRepository, never()).saveAndFlush(any());
    }

    private SubmitFeedbackRequest request() {
        SubmitFeedbackRequest request = new SubmitFeedbackRequest();
        request.setRating(5);
        request.setSatisfactionLevel(5);
        request.setComment("Great session");
        request.setWouldRecommend(true);
        request.setIsPublic(false);
        return request;
    }
}
