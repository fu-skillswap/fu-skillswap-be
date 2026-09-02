package com.fptu.exe.skillswap.modules.feedback;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateTestSupport;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.feedback.domain.SessionFeedback;
import com.fptu.exe.skillswap.modules.feedback.dto.request.SubmitFeedbackRequest;
import com.fptu.exe.skillswap.modules.feedback.dto.response.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.service.SessionFeedbackService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionFeedbackServiceTest {

    @Mock
    private SessionFeedbackRepository sessionFeedbackRepository;

    @Mock
    private BookingQueryPort bookingQueryPort;

    @Mock
    private MentorQueryPort mentorQueryPort;

    @Mock
    private NotificationCommandPort notificationCommandPort;

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private SessionFeedbackService sessionFeedbackService;

    private UUID menteeId;
    private UUID mentorId;
    private Booking booking;
    private MentorProfile mentorProfile;
    private User mentee;
    private User mentor;

    @BeforeEach
    void setUp() {
        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();

        mentee = new User();
        mentee.setId(menteeId);
        mentee.setFullName("Mentee");

        mentor = new User();
        mentor.setId(mentorId);
        mentor.setFullName("Mentor");

        mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorId);
        mentorProfile.setTotalReviews(2);
        mentorProfile.setAverageRating(new java.math.BigDecimal("4.50"));

        booking = new Booking();
        booking.setId(UUID.randomUUID());
        BookingStateTestSupport.setStatus(booking, BookingStatus.COMPLETED);
        booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
        booking.setMenteeUserId(menteeId);
        booking.setMentorUserId(mentorId);
        lenient().when(userQueryPort.existsById(mentorId)).thenReturn(true);
        lenient().when(entityManager.getReference(User.class, mentorId)).thenReturn(mentor);
        lenient().when(entityManager.getReference(User.class, menteeId)).thenReturn(mentee);
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
        BookingStateTestSupport.setStatus(booking, BookingStatus.PAID);
        when(bookingQueryPort.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request())
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void submitFeedback_mentorReviewer_shouldThrowAccessDenied() {
        when(bookingQueryPort.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(mentorId, booking.getId(), request())
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void submitFeedback_duplicateReview_shouldThrowConflict() {
        when(bookingQueryPort.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(sessionFeedbackRepository.existsByBookingIdAndReviewerId(booking.getId(), menteeId)).thenReturn(true);

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request())
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void submitFeedback_success_shouldDefaultPublicAndUpdateMentorStats() {
        when(bookingQueryPort.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(sessionFeedbackRepository.existsByBookingIdAndReviewerId(booking.getId(), menteeId)).thenReturn(false);
        when(sessionFeedbackRepository.saveAndFlush(any(SessionFeedback.class))).thenAnswer(invocation -> {
            SessionFeedback feedback = invocation.getArgument(0);
            feedback.setId(UUID.randomUUID());
            return feedback;
        });
        when(mentorQueryPort.findMentorProfileByIdForUpdate(mentorId)).thenReturn(Optional.of(mentorProfile));

        SubmitFeedbackRequest request = request();
        request.setIsPublic(null);

        SessionFeedbackResponse response = sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request);

        assertNotNull(response.getId());
        assertEquals(menteeId, response.getReviewerUserId());
        assertEquals(mentorId, response.getRevieweeUserId());
        assertEquals(3, mentorProfile.getTotalReviews());
        assertEquals("4.67", mentorProfile.getAverageRating().toString());
        assertEquals(true, response.isPublic());
        verify(mentorQueryPort).saveMentorProfile(mentorProfile);
        verify(notificationCommandPort).publish(new NotificationCommandPort.NotificationIntent(
                mentorId,
                "FEEDBACK_RECEIVED",
                "Bạn vừa nhận được đánh giá mới",
                "Mentee đã gửi đánh giá sau buổi mentoring.",
                "BOOKING",
                booking.getId(),
                "/bookings/" + booking.getId()
        ));
    }

    @Test
    void submitFeedback_autoClosedBooking_shouldSucceed() {
        booking.setCompletionOutcome(BookingCompletionOutcome.AUTO_CLOSED);
        when(bookingQueryPort.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(sessionFeedbackRepository.existsByBookingIdAndReviewerId(booking.getId(), menteeId)).thenReturn(false);
        when(sessionFeedbackRepository.saveAndFlush(any(SessionFeedback.class))).thenAnswer(invocation -> {
            SessionFeedback feedback = invocation.getArgument(0);
            feedback.setId(UUID.randomUUID());
            return feedback;
        });
        when(mentorQueryPort.findMentorProfileByIdForUpdate(mentorId)).thenReturn(Optional.of(mentorProfile));

        SessionFeedbackResponse response = sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request());

        assertNotNull(response.getId());
        assertEquals(menteeId, response.getReviewerUserId());
    }

    @Test
    void submitFeedback_nonParticipant_shouldThrowAccessDenied() {
        when(bookingQueryPort.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(UUID.randomUUID(), booking.getId(), request())
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(sessionFeedbackRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitFeedback_failedDuplicate_shouldNotCreateNotification() {
        when(bookingQueryPort.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(sessionFeedbackRepository.existsByBookingIdAndReviewerId(booking.getId(), menteeId)).thenReturn(true);

        assertThrows(BaseException.class, () ->
                sessionFeedbackService.submitFeedback(menteeId, booking.getId(), request())
        );

        verify(notificationCommandPort, never()).publish(any());
    }

    @Test
    void getBookingFeedback_byMentee_shouldReturnFeedback() {
        when(bookingQueryPort.findById(booking.getId())).thenReturn(Optional.of(booking));
        SessionFeedback feedback = SessionFeedback.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .reviewer(mentee)
                .reviewee(mentor)
                .rating(5)
                .comment("Excellent mentor")
                .isPublic(true)
                .build();
        when(sessionFeedbackRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(feedback));

        SessionFeedbackResponse response = sessionFeedbackService.getBookingFeedback(menteeId, booking.getId());

        assertNotNull(response);
        assertEquals(5, response.getRating());
        assertEquals("Excellent mentor", response.getComment());
    }

    @Test
    void getBookingFeedback_byMentor_shouldReturnFeedback() {
        when(bookingQueryPort.findById(booking.getId())).thenReturn(Optional.of(booking));
        SessionFeedback feedback = SessionFeedback.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .reviewer(mentee)
                .reviewee(mentor)
                .rating(4)
                .comment("Good session")
                .isPublic(true)
                .build();
        when(sessionFeedbackRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(feedback));

        SessionFeedbackResponse response = sessionFeedbackService.getBookingFeedback(mentorId, booking.getId());

        assertNotNull(response);
        assertEquals(4, response.getRating());
        assertEquals("Good session", response.getComment());
    }

    @Test
    void getBookingFeedback_whenNoneExists_shouldReturnNull() {
        when(bookingQueryPort.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(sessionFeedbackRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());

        SessionFeedbackResponse response = sessionFeedbackService.getBookingFeedback(menteeId, booking.getId());

        assertNull(response);
    }

    @Test
    void getBookingFeedback_unrelatedUser_shouldThrowAccessDenied() {
        when(bookingQueryPort.findById(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                sessionFeedbackService.getBookingFeedback(UUID.randomUUID(), booking.getId())
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void hasSubmittedFeedback_shouldDelegateToRepository() {
        UUID bookingId = UUID.randomUUID();
        when(sessionFeedbackRepository.existsByBookingIdAndReviewerId(bookingId, menteeId)).thenReturn(true);

        assertTrue(sessionFeedbackService.hasSubmittedFeedback(bookingId, menteeId));
        assertFalse(sessionFeedbackService.hasSubmittedFeedback(null, menteeId));
        assertFalse(sessionFeedbackService.hasSubmittedFeedback(bookingId, null));
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
