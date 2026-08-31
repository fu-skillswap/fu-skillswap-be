package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.meeting.MeetingProviderFactory;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.GoogleCalendarConnectionPort;
import com.fptu.exe.skillswap.modules.identity.port.UserLockPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingDecisionServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    @Mock
    private UserLockPort userLockPort;
    @Mock
    private MentorQueryPort mentorQueryPort;
    @Mock
    private UserQueryPort userQueryPort;
    @Mock
    private EntityManager entityManager;
    @Mock
    private SessionService sessionService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private BookingResponseMapper bookingResponseMapper;
    @Mock
    private GoogleCalendarConnectionPort googleCalendarConnectionPort;

    private MeetingProviderFactory meetingProviderFactory;
    private BookingDecisionService bookingDecisionService;

    private UUID mentorId;
    private UUID menteeId;
    private UUID bookingId;
    private MentorProfile mentorProfile;
    private MentorAvailabilitySlot slot;
    private Booking pendingBooking;
    private Session session;

    @BeforeEach
    void setUp() {
        mentorId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        User mentorUser = User.builder().id(mentorId).email("mentor@test.com").fullName("Mentor Dev").build();
        User menteeUser = User.builder().id(menteeId).email("mentee@test.com").fullName("Mentee Student").build();

        mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .userId(mentorUser.getId())
                .status(com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.ACTIVE)
                .build();

        slot = new MentorAvailabilitySlot();
        slot.setId(UUID.randomUUID());
        slot.setMentorProfile(mentorProfile);

        Instant startUtc = Instant.parse("2026-08-05T08:00:00Z");
        Instant endUtc = Instant.parse("2026-08-05T09:00:00Z");

        pendingBooking = Booking.builder()
                .id(bookingId)
                .mentorUserId(mentorProfile.getUserId())
                .mentee(menteeUser)
                .slot(slot)
                .status(BookingStatus.PENDING)
                .selectedStartTimeUtc(startUtc)
                .selectedEndTimeUtc(endUtc)
                .serviceIsFreeSnapshot(false)
                .build();

        session = Session.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorUser.getId())
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(bookingId)
                .scheduledStartTimeUtc(startUtc)
                .scheduledEndTimeUtc(endUtc)
                .status(SessionStatus.SCHEDULED)
                .build();

        meetingProviderFactory = new MeetingProviderFactory(Collections.emptyList());

        bookingDecisionService = new BookingDecisionService(
                bookingRepository,
                mentorAvailabilitySlotRepository,
                userLockPort,
                userQueryPort,
                sessionService,
                eventPublisher,
                bookingResponseMapper,
                googleCalendarConnectionPort,
                meetingProviderFactory
        );
        bookingDecisionService.setTimeProvider(TimeProvider.from(Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC)));

        when(bookingRepository.findByIdForMentorDecision(bookingId)).thenReturn(Optional.of(pendingBooking));
        when(mentorQueryPort.findMentorProfileByIdForUpdate(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(userLockPort.lockUsersForUpdate(any())).thenReturn(List.of(menteeUser, mentorUser));
        when(bookingRepository.findOverlappingBySlotIdAndStatusForUpdateUtc(any(), any(), any(), any()))
                .thenReturn(List.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.createForAcceptedBooking(any(Booking.class))).thenReturn(session);
        BookingResponse mockBookingResponse = mock(BookingResponse.class);
        when(bookingResponseMapper.toBookingResponse(any(Booking.class))).thenReturn(mockBookingResponse);
    }

    @Test
    void acceptBooking_whenMentorHasGoogleCalendar_andNoMeetingLink_shouldSucceed() {
        when(googleCalendarConnectionPort.hasActiveConnection(mentorId)).thenReturn(true);

        AcceptBookingRequest request = new AcceptBookingRequest("Sẵn sàng hướng dẫn em.");

        BookingResponse response = bookingDecisionService.acceptBooking(mentorId, bookingId, request);

        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, pendingBooking.getStatus());
        assertEquals("Sẵn sàng hướng dẫn em.", pendingBooking.getMentorResponseNote());
        assertNull(pendingBooking.getMeetingPlatform());
        assertNull(pendingBooking.getMeetingLink());
    }

    @Test
    void acceptBooking_whenMentorHasGoogleCalendar_andCustomLinkProvided_shouldSucceedWithCustomLinkOnBooking() {
        when(googleCalendarConnectionPort.hasActiveConnection(mentorId)).thenReturn(true);

        AcceptBookingRequest request = new AcceptBookingRequest(
                "Dùng Google Meet",
                MeetingPlatform.GOOGLE_MEET,
                "https://meet.google.com/abc-defg-hij",
                null
        );

        BookingResponse response = bookingDecisionService.acceptBooking(mentorId, bookingId, request);

        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, pendingBooking.getStatus());
        assertEquals(MeetingPlatform.GOOGLE_MEET, pendingBooking.getMeetingPlatform());
        assertEquals("https://meet.google.com/abc-defg-hij", pendingBooking.getMeetingLink());
    }

    @Test
    void acceptBooking_whenMentorHasNoGoogleCalendar_andNoMeetingPlatform_shouldThrowBadRequest() {
        when(googleCalendarConnectionPort.hasActiveConnection(mentorId)).thenReturn(false);

        AcceptBookingRequest request = new AcceptBookingRequest("Note only");

        BaseException ex = assertThrows(BaseException.class, () ->
                bookingDecisionService.acceptBooking(mentorId, bookingId, request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Vui lòng chọn nền tảng phòng học"));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void acceptBooking_whenMentorHasNoGoogleCalendar_andMissingMeetingLink_shouldThrowBadRequest() {
        when(googleCalendarConnectionPort.hasActiveConnection(mentorId)).thenReturn(false);

        AcceptBookingRequest request = new AcceptBookingRequest(
                "Zoom note",
                MeetingPlatform.ZOOM,
                "   ",
                null
        );

        BaseException ex = assertThrows(BaseException.class, () ->
                bookingDecisionService.acceptBooking(mentorId, bookingId, request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Link phòng học là bắt buộc"));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void acceptBooking_whenMentorHasNoGoogleCalendar_andValidMeetingLink_shouldSucceedAndSaveOnBooking() {
        when(googleCalendarConnectionPort.hasActiveConnection(mentorId)).thenReturn(false);

        AcceptBookingRequest request = new AcceptBookingRequest(
                "Học qua Zoom nhé",
                MeetingPlatform.ZOOM,
                "https://us02web.zoom.us/j/1234567890",
                null
        );

        BookingResponse response = bookingDecisionService.acceptBooking(mentorId, bookingId, request);

        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, pendingBooking.getStatus());
        assertEquals(MeetingPlatform.ZOOM, pendingBooking.getMeetingPlatform());
        assertEquals("https://us02web.zoom.us/j/1234567890", pendingBooking.getMeetingLink());
    }

    @Test
    void acceptBooking_whenMentorHasNoGoogleCalendar_andOfflineSelectedWithLocation_shouldSucceed() {
        when(googleCalendarConnectionPort.hasActiveConnection(mentorId)).thenReturn(false);

        AcceptBookingRequest request = new AcceptBookingRequest(
                "Gặp tại campus FPTU",
                MeetingPlatform.OFFLINE,
                null,
                "Thư viện FPTU HCM - Tầng 2 Bàn 12"
        );

        BookingResponse response = bookingDecisionService.acceptBooking(mentorId, bookingId, request);

        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, pendingBooking.getStatus());
        assertEquals("Thư viện FPTU HCM - Tầng 2 Bàn 12", pendingBooking.getLocation());
        assertEquals(MeetingPlatform.OFFLINE, pendingBooking.getMeetingPlatform());
        assertNull(pendingBooking.getMeetingLink());
    }

    @Test
    void acceptBooking_freeBooking_createsSessionImmediately() {
        pendingBooking.setServiceIsFreeSnapshot(true);
        when(googleCalendarConnectionPort.hasActiveConnection(mentorId)).thenReturn(false);

        AcceptBookingRequest request = new AcceptBookingRequest(
                "Học qua Discord nhé",
                MeetingPlatform.DISCORD,
                "https://discord.gg/skillswap",
                null
        );

        BookingResponse response = bookingDecisionService.acceptBooking(mentorId, bookingId, request);

        assertEquals(BookingStatus.PAID, pendingBooking.getStatus());
        verify(sessionService).createForAcceptedBooking(pendingBooking);
        verify(conversationService).createDirectForAcceptedBooking(pendingBooking);
    }
}
