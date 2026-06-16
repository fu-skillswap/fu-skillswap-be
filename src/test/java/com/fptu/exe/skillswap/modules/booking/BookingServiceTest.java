package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
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
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
                .verifiedAt(LocalDateTime.now().minusDays(3))
                .isAvailable(true)
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
        slot.setStartTime(LocalDateTime.now().plusDays(2));
        slot.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
        slot.setActive(true);
        slot.setBooked(false);
    }

    @Test
    void createBooking_successful_marksSlotBookedAndMapsResponse() {
        CreateBookingRequest request = new CreateBookingRequest(
                mentorId,
                slot.getId(),
                null,
                "Learn Spring Modulith",
                "Understand module dependencies"
        );

        when(userRepository.findById(menteeId)).thenReturn(Optional.of(mentee));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));

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
        assertEquals(mentorId, response.mentorUserId());
        assertEquals(menteeId, response.menteeUserId());
        assertTrue(slot.isBooked());
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
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));

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
        when(bookingRepository.findByIdForMentorDecision(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        BookingResponse response = bookingService.acceptBooking(mentorId, booking.getId(), new AcceptBookingRequest("Accepted"));

        assertEquals(BookingStatus.ACCEPTED, response.status());
        assertEquals("Accepted", booking.getMentorResponseNote());
        assertNotNull(booking.getAcceptedAt());
    }

    @Test
    void rejectBooking_successful_releasesSlotAndCountsRejected() {
        Booking booking = bookingForDecision(BookingStatus.PENDING);
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
        booking.setRequestedStartTime(LocalDateTime.now().minusHours(1));
        booking.setRequestedEndTime(LocalDateTime.now().plusMinutes(10));
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
    }

    @Test
    void completeBooking_beforeStart_shouldThrowConflict() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(LocalDateTime.now().plusHours(2));
        booking.setRequestedEndTime(LocalDateTime.now().plusHours(3));
        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.completeBooking(
                mentorId,
                booking.getId(),
                new CompleteBookingRequest("Too early")
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void cancelBookingByMentor_withinTwelveHours_shouldApplyPenalty() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(LocalDateTime.now().plusHours(8));
        booking.setRequestedEndTime(LocalDateTime.now().plusHours(9));
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
        booking.setRequestedStartTime(LocalDateTime.now().plusHours(3));
        booking.setRequestedEndTime(LocalDateTime.now().plusHours(4));
        when(bookingRepository.findByIdForCancellation(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.cancelBookingByMentor(mentorId, booking.getId(), new CancelBookingRequest("Emergency"));

        assertNotNull(mentorProfile.getBookingSuspendedUntil());
        assertTrue(mentorProfile.getBookingSuspendedUntil().isAfter(LocalDateTime.now().plusDays(2)));
    }

    @Test
    void cancelBookingByMentee_pending_shouldReleaseSlot() {
        Booking booking = bookingForDecision(BookingStatus.PENDING);
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
    void cancelBookingByMentee_acceptedWithinTwelveHours_shouldThrowConflict() {
        Booking booking = bookingForDecision(BookingStatus.ACCEPTED);
        booking.setRequestedStartTime(LocalDateTime.now().plusHours(6));
        when(bookingRepository.findByIdForCancellation(booking.getId())).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> bookingService.cancelBookingByMentee(
                menteeId,
                booking.getId(),
                new CancelBookingRequest("Too late")
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
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

    private Booking bookingForDecision(BookingStatus status) {
        slot.setBooked(true);
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
}
