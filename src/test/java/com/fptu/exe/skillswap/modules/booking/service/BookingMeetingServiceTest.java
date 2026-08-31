package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.service.meeting.MeetingProviderFactory;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingMeetingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private BookingResponseMapper bookingResponseMapper;
    @Mock
    private UserQueryPort userQueryPort;

    private MeetingProviderFactory meetingProviderFactory;
    private BookingMeetingService bookingMeetingService;

    private UUID mentorId;
    private UUID menteeId;
    private UUID bookingId;
    private Booking booking;
    private Session session;

    @BeforeEach
    void setUp() {
        mentorId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        User menteeUser = mock(User.class);
        when(menteeUser.getId()).thenReturn(menteeId);

        Instant startUtc = Instant.parse("2026-09-01T10:00:00Z");
        Instant endUtc = Instant.parse("2026-09-01T11:00:00Z");

        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorId)
                .startTimeUtc(startUtc)
                .endTimeUtc(endUtc)
                .isActive(true)
                .isBooked(true)
                .build();

        booking = Booking.builder()
                .id(bookingId)
                .mentorUserId(mentorId)
                .mentee(menteeUser)
                .slot(slot)
                .status(BookingStatus.PAID)
                .selectedStartTimeUtc(startUtc)
                .selectedEndTimeUtc(endUtc)
                .learningGoalTitle("Spring Boot")
                .meetingPlatform(MeetingPlatform.ZOOM)
                .meetingLink("https://zoom.us/j/old-link")
                .build();

        session = Session.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorId)
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(bookingId)
                .scheduledStartTimeUtc(startUtc)
                .scheduledEndTimeUtc(endUtc)
                .meetingPlatform(MeetingPlatform.ZOOM)
                .meetingLink("https://zoom.us/j/old-link")
                .googleCalendarManaged(false)
                .status(SessionStatus.SCHEDULED)
                .build();

        meetingProviderFactory = new MeetingProviderFactory(Collections.emptyList());

        bookingMeetingService = new BookingMeetingService(
                bookingRepository,
                sessionService,
                eventPublisher,
                meetingProviderFactory,
                bookingResponseMapper,
                userQueryPort
        );
        bookingMeetingService.setTimeProvider(TimeProvider.from(Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC)));

        when(bookingRepository.findByIdForMentorDecision(bookingId)).thenReturn(Optional.of(booking));
        when(userQueryPort.findUserSummaryById(mentorId)).thenReturn(Optional.empty());
        when(sessionService.findByBookingId(bookingId)).thenReturn(session);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        BookingResponse mockBookingResponse = mock(BookingResponse.class);
        when(bookingResponseMapper.toBookingResponse(any(Booking.class))).thenReturn(mockBookingResponse);
    }

    @Test
    void saveMeetingLink_whenNotGoogleCalendarManaged_shouldUpdateLinkAndNotify() {
        SaveMeetingLinkRequest request = new SaveMeetingLinkRequest(
                MeetingPlatform.DISCORD,
                "https://discord.gg/new-channel",
                null
        );

        BookingResponse response = bookingMeetingService.saveMeetingLink(mentorId, bookingId, request);

        assertEquals(MeetingPlatform.DISCORD, session.getMeetingPlatform());
        assertEquals("https://discord.gg/new-channel", session.getMeetingLink());
        assertFalse(session.isGoogleCalendarManaged());
        verify(sessionService).save(session);
        verify(bookingRepository).save(booking);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream().anyMatch(e -> e instanceof NotificationEvent));
    }

    @Test
    void saveMeetingLink_whenGoogleCalendarManaged_shouldThrowResourceConflict() {
        session.setGoogleCalendarManaged(true);

        SaveMeetingLinkRequest request = new SaveMeetingLinkRequest(
                MeetingPlatform.ZOOM,
                "https://zoom.us/j/999999",
                null
        );

        BaseException ex = assertThrows(BaseException.class, () ->
                bookingMeetingService.saveMeetingLink(mentorId, bookingId, request)
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Thông tin cuộc họp đang được Google Calendar quản lý"));
    }

    @Test
    void saveMeetingLink_whenSessionAlreadyStarted_shouldThrowResourceConflict() {
        bookingMeetingService.setTimeProvider(TimeProvider.from(Clock.fixed(Instant.parse("2026-09-01T10:30:00Z"), ZoneOffset.UTC)));

        SaveMeetingLinkRequest request = new SaveMeetingLinkRequest(
                MeetingPlatform.DISCORD,
                "https://discord.gg/new-link",
                null
        );

        BaseException ex = assertThrows(BaseException.class, () ->
                bookingMeetingService.saveMeetingLink(mentorId, bookingId, request)
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Không thể cập nhật thông tin phòng học sau khi buổi học đã bắt đầu"));
    }

    @Test
    void saveMeetingLink_whenOfflineWithoutLocationOrLink_shouldThrowBadRequest() {
        SaveMeetingLinkRequest request = new SaveMeetingLinkRequest(
                MeetingPlatform.OFFLINE,
                null,
                "   "
        );

        BaseException ex = assertThrows(BaseException.class, () ->
                bookingMeetingService.saveMeetingLink(mentorId, bookingId, request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Địa điểm hoặc thông tin gặp mặt là bắt buộc đối với hình thức Offline"));
    }

    @Test
    void saveMeetingLink_whenOfflineWithLocation_shouldSucceed() {
        SaveMeetingLinkRequest request = new SaveMeetingLinkRequest(
                MeetingPlatform.OFFLINE,
                null,
                "Toà nhà Innovation - Phòng Lab 3"
        );

        BookingResponse response = bookingMeetingService.saveMeetingLink(mentorId, bookingId, request);

        assertEquals(MeetingPlatform.OFFLINE, session.getMeetingPlatform());
        assertNull(session.getMeetingLink());
        assertEquals("Toà nhà Innovation - Phòng Lab 3", booking.getLocation());
        verify(sessionService).save(session);
    }
}
