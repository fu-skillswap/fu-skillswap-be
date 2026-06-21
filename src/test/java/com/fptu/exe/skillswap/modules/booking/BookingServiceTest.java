package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.booking.dto.BookingViewRole;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Mock
    private MentorServiceRepository mentorServiceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AcademicService academicService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private UUID menteeId;
    private UUID mentorId;
    private User mentee;
    private User mentorUser;
    private MentorProfile mentorProfile;
    private MentorAvailabilitySlot slot;

    @BeforeEach
    void setUp() {
        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();

        mentee = buildUser(menteeId, "mentee@test.com", "Mentee User", UserStatus.ACTIVE);
        mentorUser = buildUser(mentorId, "mentor@test.com", "Mentor User", UserStatus.ACTIVE);

        mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .verifiedAt(testNow().minusDays(3))
                .isAvailable(true)
                .headline("Backend Mentor")
                .expertiseDescription("Support Spring Boot and PostgreSQL")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .averageRating(BigDecimal.valueOf(4.8))
                .totalReviews(5)
                .totalCompletedSessions(2)
                .totalSessions(2)
                .lateCancellationPenaltyPoints(BigDecimal.ZERO)
                .build();

        slot = new MentorAvailabilitySlot();
        slot.setId(UUID.randomUUID());
        slot.setMentorProfile(mentorProfile);
        slot.setStartTime(testNow().plusDays(2));
        slot.setEndTime(testNow().plusDays(2).plusHours(1));
        slot.setActive(true);
        slot.setBooked(false);
    }

    @Test
    void createBooking_successful_keepsSlotUnbookedWhilePendingAndMapsResponse() {
        CreateBookingRequest request = new CreateBookingRequest(
                mentorId,
                slot.getId(),
                null,
                "Learn Spring Modulith",
                "Understand module dependencies"
        );

        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(academicService.hasCompletedStudentProfile(menteeId)).thenReturn(true);
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING)).thenReturn(0L);
        when(bookingRepository.existsByMenteeIdAndSlotIdAndStatusIn(eq(menteeId), eq(slot.getId()), any())).thenReturn(false);

        Booking savedBooking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.PENDING)
                .learningGoalTitle(request.learningGoalTitle())
                .learningGoalDescription(request.learningGoalDescription())
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build();

        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingResponse response = bookingService.createBooking(menteeId, request);

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING, response.status());
        assertEquals(BookingStatus.PENDING, response.sessionStatus());
        assertEquals(savedBooking.getId(), response.sessionId());
        assertEquals(mentorId, response.mentorUserId());
        assertEquals(menteeId, response.menteeUserId());
        assertFalse(slot.isBooked());
    }

    @Test
    void createBooking_suspendedMentor_shouldThrowConflict() {
        mentorProfile.setBookingSuspendedUntil(LocalDateTime.now().plusDays(1));
        CreateBookingRequest request = new CreateBookingRequest(
                mentorId,
                slot.getId(),
                null,
                "Need support",
                null
        );

        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(academicService.hasCompletedStudentProfile(menteeId)).thenReturn(true);
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createBooking_withoutCompletedAcademicProfile_shouldThrowConflict() {
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(academicService.hasCompletedStudentProfile(menteeId)).thenReturn(false);

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createBooking_adminAccount_shouldThrowAccessDenied() {
        mentee.setRoles(Set.of(RoleCode.ADMIN));
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void createBooking_systemAdminAccount_shouldThrowAccessDenied() {
        mentee.setRoles(Set.of(RoleCode.SYSTEM_ADMIN));
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void createBooking_mentorUserBookingAnotherMentor_shouldSucceed() {
        User mentorBooker = buildUser(UUID.randomUUID(), "mentor-booker@test.com", "Mentor Booker", UserStatus.ACTIVE);
        mentorBooker.setRoles(Set.of(RoleCode.MENTOR));
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Need architecture advice", null);

        when(userRepository.findById(mentorBooker.getId())).thenReturn(Optional.of(mentorBooker));
        when(academicService.hasCompletedStudentProfile(mentorBooker.getId())).thenReturn(true);
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING)).thenReturn(0L);
        when(bookingRepository.existsByMenteeIdAndSlotIdAndStatusIn(eq(mentorBooker.getId()), eq(slot.getId()), any())).thenReturn(false);

        Booking savedBooking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentorBooker)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.PENDING)
                .learningGoalTitle(request.learningGoalTitle())
                .learningGoalDescription(request.learningGoalDescription())
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build();

        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingResponse response = bookingService.createBooking(mentorBooker.getId(), request);

        assertEquals(mentorBooker.getId(), response.menteeUserId());
        assertEquals(BookingStatus.PENDING, response.status());
    }

    @Test
    void createBooking_allowsUpToThreePendingRequestsForSameSlot() {
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(academicService.hasCompletedStudentProfile(menteeId)).thenReturn(true);
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING)).thenReturn(2L);
        when(bookingRepository.existsByMenteeIdAndSlotIdAndStatusIn(eq(menteeId), eq(slot.getId()), any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(menteeId, request);

        assertEquals(BookingStatus.PENDING, response.status());
        assertFalse(slot.isBooked());
    }

    @Test
    void createBooking_fourthPendingRequestForSameSlot_shouldFail() {
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(academicService.hasCompletedStudentProfile(menteeId)).thenReturn(true);
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING)).thenReturn(3L);

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createBooking_shouldFailIfSlotAlreadyHasAcceptedBooking() {
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(academicService.hasCompletedStudentProfile(menteeId)).thenReturn(true);
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(true);

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createBooking_sameMenteeSameSlotPendingOrAccepted_shouldFail() {
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(academicService.hasCompletedStudentProfile(menteeId)).thenReturn(true);
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING)).thenReturn(1L);
        when(bookingRepository.existsByMenteeIdAndSlotIdAndStatusIn(eq(menteeId), eq(slot.getId()), any())).thenReturn(true);

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void createBooking_inactiveMentee_shouldThrowUserInactive() {
        mentee.setStatus(UserStatus.BANNED);
        CreateBookingRequest request = new CreateBookingRequest(mentorId, slot.getId(), null, "Goal", null);
        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.createBooking(menteeId, request));

        assertEquals(ErrorCode.USER_INACTIVE, exception.getErrorCode());
    }

    @Test
    void acceptBooking_successful_setsAcceptedAtAndResponseNote() {
        Booking booking = bookingForDecision(BookingStatus.PENDING);
        slot.setBooked(false);
        when(bookingRepository.findByIdForMentorDecision(booking.getId())).thenReturn(Optional.of(booking));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.findBySlotIdAndStatusForUpdate(slot.getId(), BookingStatus.PENDING)).thenReturn(List.of(booking));
        when(bookingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.acceptBooking(mentorId, booking.getId(), new AcceptBookingRequest("Accepted"));

        assertEquals(BookingStatus.ACCEPTED, response.status());
        assertEquals("Accepted", booking.getMentorResponseNote());
        assertNotNull(booking.getAcceptedAt());
        assertTrue(slot.isBooked());
    }

    @Test
    void mentorAccept_oneBooking_shouldAcceptItAndAutoRejectOtherPendingBookingsSameSlot() {
        Booking selectedBooking = bookingForDecision(BookingStatus.PENDING);
        Booking otherPendingBooking = bookingForDecision(BookingStatus.PENDING);
        otherPendingBooking.setId(UUID.randomUUID());
        slot.setBooked(false);

        when(bookingRepository.findByIdForMentorDecision(selectedBooking.getId())).thenReturn(Optional.of(selectedBooking));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.findBySlotIdAndStatusForUpdate(slot.getId(), BookingStatus.PENDING))
                .thenReturn(List.of(selectedBooking, otherPendingBooking));
        when(bookingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(selectedBooking)).thenReturn(selectedBooking);

        BookingResponse response = bookingService.acceptBooking(mentorId, selectedBooking.getId(), new AcceptBookingRequest("Accepted"));

        assertEquals(BookingStatus.ACCEPTED, response.status());
        assertTrue(slot.isBooked());
        assertEquals(BookingStatus.REJECTED, otherPendingBooking.getStatus());
        assertEquals(BookingQueueConstants.AUTO_REJECT_SLOT_ACCEPTED_REASON, otherPendingBooking.getRejectReason());
        assertNotNull(otherPendingBooking.getRejectedAt());
    }

    @Test
    void rejectBooking_successful_keepsSlotAvailableAndCountsRejected() {
        Booking booking = bookingForDecision(BookingStatus.PENDING);
        slot.setBooked(false);
        mentorProfile.setTotalRejectedBookings(1);
        when(bookingRepository.findByIdForMentorDecision(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.rejectBooking(
                mentorId,
                booking.getId(),
                new RejectBookingRequest("Busy", "Please rebook")
        );

        assertEquals(BookingStatus.REJECTED, response.status());
        assertFalse(slot.isBooked());
        assertEquals(2, mentorProfile.getTotalRejectedBookings());
        assertEquals("Busy", booking.getRejectReason());
    }

    @Test
    void mentorReject_onePendingRequest_shouldNotBookOrDeactivateSlot() {
        Booking booking = bookingForDecision(BookingStatus.PENDING);
        slot.setBooked(false);
        slot.setActive(true);
        when(bookingRepository.findByIdForMentorDecision(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.rejectBooking(mentorId, booking.getId(), new RejectBookingRequest("Not fit", null));

        assertEquals(BookingStatus.REJECTED, response.status());
        assertFalse(slot.isBooked());
        assertTrue(slot.isActive());
    }

    @Test
    void saveMeetingLink_nonAcceptedBooking_shouldThrowConflict() {
        Booking booking = bookingForDecision(BookingStatus.PENDING);
        when(bookingRepository.findByIdForMentorDecision(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.saveMeetingLink(
                mentorId,
                booking.getId(),
                new SaveMeetingLinkRequest(MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/abc", null)
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void saveMeetingLink_invalidUrl_shouldThrowBadRequest() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        when(bookingRepository.findByIdForMentorDecision(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.saveMeetingLink(
                mentorId,
                booking.getId(),
                new SaveMeetingLinkRequest(MeetingPlatform.GOOGLE_MEET, "not-a-url", "Room 201")
        ));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void completeBooking_asMentor_shouldUpdateCompletionAndCounters() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(testNow().minusHours(1));
        booking.setRequestedEndTime(testNow().plusMinutes(10));
        mentorProfile.setTotalCompletedSessions(4);
        mentorProfile.setTotalSessions(8);

        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.completeBooking(
                mentorId,
                booking.getId(),
                new CompleteBookingRequest("Session completed well")
        );

        assertEquals(BookingStatus.COMPLETED, response.status());
        assertEquals("Session completed well", booking.getMentorNote());
        assertNotNull(booking.getCompletedAt());
        assertEquals(5, mentorProfile.getTotalCompletedSessions());
        assertEquals(9, mentorProfile.getTotalSessions());
        assertEquals(booking.getRequestedStartTime(), booking.getActualStartTime());
        assertEquals(booking.getRequestedEndTime(), booking.getActualEndTime());
        verify(notificationService).createNotification(
                eq(menteeId),
                eq(com.fptu.exe.skillswap.modules.notification.domain.NotificationType.SESSION_COMPLETED),
                eq("Buổi mentoring đã được hoàn tất"),
                eq("Buổi mentoring của bạn đã được đánh dấu hoàn tất."),
                eq("BOOKING"),
                eq(booking.getId())
        );
    }

    @Test
    void completeBooking_asMentee_shouldNotifyMentor() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(testNow().minusHours(1));
        booking.setRequestedEndTime(testNow().plusMinutes(10));

        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.completeBooking(
                menteeId,
                booking.getId(),
                new CompleteBookingRequest("Done")
        );

        assertEquals(BookingStatus.COMPLETED, response.status());
        assertEquals("Done", booking.getMenteeNote());
        verify(notificationService).createNotification(
                eq(mentorId),
                eq(com.fptu.exe.skillswap.modules.notification.domain.NotificationType.SESSION_COMPLETED),
                eq("Buổi mentoring đã được hoàn tất"),
                eq("Buổi mentoring với Mentee User đã được đánh dấu hoàn tất."),
                eq("BOOKING"),
                eq(booking.getId())
        );
    }

    @Test
    void completeBooking_beforeStart_shouldThrowConflict() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(testNow().plusHours(2));
        booking.setRequestedEndTime(testNow().plusHours(3));
        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.completeBooking(
                mentorId,
                booking.getId(),
                new CompleteBookingRequest("Too early")
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelBookingByMentor_withinTwelveHours_shouldApplyPenalty() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(testNow().plusHours(8));
        booking.setRequestedEndTime(testNow().plusHours(9));
        mentorProfile.setLateCancellationPenaltyPoints(BigDecimal.ZERO);
        when(bookingRepository.findByIdForCancellation(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.cancelBookingByMentor(
                mentorId,
                booking.getId(),
                new CancelBookingRequest("Unexpected conflict")
        );

        assertEquals(BookingStatus.CANCELLED_BY_MENTOR, response.status());
        assertEquals(BigDecimal.valueOf(0.5), mentorProfile.getLateCancellationPenaltyPoints());
        assertFalse(slot.isBooked());
        assertFalse(slot.isActive());
    }

    @Test
    void cancelBookingByMentor_underSixHours_shouldSuspendMentor() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(testNow().plusHours(3));
        booking.setRequestedEndTime(testNow().plusHours(4));
        when(bookingRepository.findByIdForCancellation(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.cancelBookingByMentor(mentorId, booking.getId(), new CancelBookingRequest("Emergency"));

        assertNotNull(mentorProfile.getBookingSuspendedUntil());
        assertTrue(mentorProfile.getBookingSuspendedUntil().isAfter(LocalDateTime.now().plusDays(2)));
    }

    @Test
    void menteeCancel_pendingRequest_shouldNotChangeSlotBooked() {
        Booking booking = bookingForDecision(BookingStatus.PENDING);
        slot.setBooked(false);
        when(bookingRepository.findByIdForCancellation(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.cancelBookingByMentee(
                menteeId,
                booking.getId(),
                new CancelBookingRequest("No longer needed")
        );

        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, response.status());
        assertFalse(slot.isBooked());
    }

    @Test
    void cancelBookingByMentee_acceptedBeforeEightHours_shouldReleaseSlot() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(testNow().plusHours(9));
        booking.setRequestedEndTime(testNow().plusHours(10));
        when(bookingRepository.findByIdForCancellation(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.cancelBookingByMentee(
                menteeId,
                booking.getId(),
                new CancelBookingRequest("Too late")
        );

        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, response.status());
        assertFalse(slot.isBooked());
        assertTrue(slot.isActive());
        assertNotNull(response.cancelledAt());
    }

    @Test
    void cancelBookingByMentee_acceptedWithinEightHours_shouldDeactivateSlot() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(testNow().plusHours(6));
        booking.setRequestedEndTime(testNow().plusHours(7));
        when(bookingRepository.findByIdForCancellation(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.cancelBookingByMentee(
                menteeId,
                booking.getId(),
                new CancelBookingRequest("Too late")
        );

        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, response.status());
        assertFalse(slot.isBooked());
        assertFalse(slot.isActive());
        assertNotNull(response.cancelledAt());
    }

    @Test
    void getMyBookings_asMentorWithStatus_shouldQueryCorrectRepository() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        BookingListRequest request = new BookingListRequest();
        request.setRole(BookingViewRole.MENTOR);
        request.setStatus(BookingStatus.ACCEPTED);

        when(bookingRepository.findByMentorProfileUserIdAndStatus(eq(mentorId), eq(BookingStatus.ACCEPTED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking)));

        PageResponse<BookingResponse> response = bookingService.getMyBookings(mentorId, request);

        assertEquals(1, response.getContent().size());
        assertEquals(mentorId, response.getContent().getFirst().mentorUserId());
    }

    @Test
    void getAdminBookings_shouldUseSearchForAdminAndMapResponse() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        AdminBookingListRequest request = new AdminBookingListRequest();
        request.setStatus(BookingStatus.ACCEPTED);
        request.setMentorUserId(mentorId);
        request.setMenteeUserId(menteeId);

        when(bookingRepository.searchForAdmin(eq(BookingStatus.ACCEPTED), eq(mentorId), eq(menteeId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking)));

        PageResponse<BookingResponse> response = bookingService.getAdminBookings(request);

        assertEquals(1, response.getContent().size());
        assertEquals(BookingStatus.ACCEPTED, response.getContent().getFirst().status());
    }

    @Test
    void getBookingDetail_userOutsideBooking_shouldThrowUnauthorized() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () ->
                bookingService.getBookingDetail(UUID.randomUUID(), booking.getId())
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void rejectAllPendingBookingsForMentor_successful() {
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .status(BookingStatus.PENDING)
                .slot(slot)
                .build();

        when(bookingRepository.findByMentorProfileUserIdAndStatus(mentorId, BookingStatus.PENDING))
                .thenReturn(Collections.singletonList(booking));

        bookingService.rejectAllPendingBookingsForMentor(mentorId, "Banned reason");

        assertEquals(BookingStatus.REJECTED, booking.getStatus());
        assertFalse(slot.isBooked());
        verify(bookingRepository).saveAll(any());
    }

    @Test
    void getAdminBookingDetail_notFound_shouldThrowNotFound() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.getAdminBookingDetail(bookingId));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void terminalStatuses_shouldRejectFurtherActions() {
        for (BookingStatus status : List.of(
                BookingStatus.REJECTED,
                BookingStatus.CANCELLED_BY_MENTEE,
                BookingStatus.CANCELLED_BY_MENTOR,
                BookingStatus.COMPLETED
        )) {
            Booking mentorDecisionBooking = bookingForDecision(status);
            when(bookingRepository.findByIdForMentorDecision(mentorDecisionBooking.getId())).thenReturn(Optional.of(mentorDecisionBooking));
            assertThrows(BaseException.class, () -> bookingService.acceptBooking(mentorId, mentorDecisionBooking.getId(), new AcceptBookingRequest("Nope")));
            assertThrows(BaseException.class, () -> bookingService.rejectBooking(mentorId, mentorDecisionBooking.getId(), new RejectBookingRequest("Nope", null)));

            Booking cancellationBooking = bookingForDecision(status);
            when(bookingRepository.findByIdForCancellation(cancellationBooking.getId())).thenReturn(Optional.of(cancellationBooking));
            assertThrows(BaseException.class, () -> bookingService.cancelBookingByMentee(menteeId, cancellationBooking.getId(), new CancelBookingRequest("Nope")));
            assertThrows(BaseException.class, () -> bookingService.cancelBookingByMentor(mentorId, cancellationBooking.getId(), new CancelBookingRequest("Nope")));

            Booking completionBooking = bookingForDecision(status);
            when(bookingRepository.findByIdForSessionUpdate(completionBooking.getId())).thenReturn(Optional.of(completionBooking));
            assertThrows(BaseException.class, () -> bookingService.completeBooking(mentorId, completionBooking.getId(), new CompleteBookingRequest("Nope")));
        }
    }

    @Test
    void sessionStatus_shouldMirrorBookingStatus() {
        for (BookingStatus status : BookingStatus.values()) {
            Booking booking = bookingForDecision(status);
            BookingResponse response = ReflectionTestUtils.invokeMethod(bookingService, "toBookingResponse", booking);
            assertEquals(status, response.status());
            assertEquals(status, response.sessionStatus());
        }
    }

    @Test
    void actionSpecificFields_shouldOnlyChangeInMatchingActions() {
        Booking acceptedBooking = bookingForDecision(BookingStatus.PENDING);
        when(bookingRepository.findByIdForMentorDecision(acceptedBooking.getId())).thenReturn(Optional.of(acceptedBooking));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED)).thenReturn(false);
        when(bookingRepository.findBySlotIdAndStatusForUpdate(slot.getId(), BookingStatus.PENDING)).thenReturn(List.of(acceptedBooking));
        when(bookingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(acceptedBooking)).thenReturn(acceptedBooking);

        bookingService.acceptBooking(mentorId, acceptedBooking.getId(), new AcceptBookingRequest("Accepted note"));
        assertEquals("Accepted note", acceptedBooking.getMentorResponseNote());
        assertNull(acceptedBooking.getRejectReason());
        assertNull(acceptedBooking.getCancelReason());

        Booking rejectedBooking = bookingForDecision(BookingStatus.PENDING);
        when(bookingRepository.findByIdForMentorDecision(rejectedBooking.getId())).thenReturn(Optional.of(rejectedBooking));
        when(bookingRepository.save(rejectedBooking)).thenReturn(rejectedBooking);

        bookingService.rejectBooking(mentorId, rejectedBooking.getId(), new RejectBookingRequest("Reject reason", "Reject note"));
        assertEquals("Reject reason", rejectedBooking.getRejectReason());
        assertEquals("Reject note", rejectedBooking.getMentorResponseNote());
        assertNull(rejectedBooking.getCancelReason());

        Booking cancelledBooking = bookingForDecision(BookingStatus.PENDING);
        when(bookingRepository.findByIdForCancellation(cancelledBooking.getId())).thenReturn(Optional.of(cancelledBooking));
        when(bookingRepository.save(cancelledBooking)).thenReturn(cancelledBooking);

        bookingService.cancelBookingByMentee(menteeId, cancelledBooking.getId(), new CancelBookingRequest("Cancel reason"));
        assertEquals("Cancel reason", cancelledBooking.getCancelReason());
        assertNull(cancelledBooking.getRejectReason());
    }

    private Booking bookingForDecision(BookingStatus status) {
        slot.setBooked(status == BookingStatus.ACCEPTED || status == BookingStatus.COMPLETED);
        slot.setActive(true);
        return Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .service(MentorService.builder().id(UUID.randomUUID()).title("Mock service").build())
                .slot(slot)
                .status(status)
                .learningGoalTitle("Need help")
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build();
    }

    private User buildUser(UUID id, String email, String fullName, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setAvatarUrl("https://img.test/" + id);
        user.setStatus(status);
        user.setRoles(Set.of());
        return user;
    }

    private LocalDateTime testNow() {
        return DateTimeUtil.now();
    }
}
