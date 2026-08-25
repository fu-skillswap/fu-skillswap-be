package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendance;
import com.fptu.exe.skillswap.modules.booking.domain.SessionParticipantRole;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionAttendanceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAttendanceServiceTest {

    private static final Instant START = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T09:00:00Z");

    @Mock private BookingRepository bookingRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SessionAttendanceRepository attendanceRepository;

    private SessionAttendanceService service;
    private UUID bookingId;
    private UUID mentorId;
    private UUID menteeId;
    private Session session;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new SessionAttendanceService(bookingRepository, sessionRepository, attendanceRepository);
        bookingId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        User mentor = User.builder().id(mentorId).email("mentor@test.com").build();
        booking = Booking.builder()
                .id(bookingId)
                .status(BookingStatus.PAID)
                .mentorProfile(MentorProfile.builder().userId(mentorId).user(mentor).build())
                .mentee(User.builder().id(menteeId).email("mentee@test.com").build())
                .selectedStartTimeUtc(START)
                .selectedStartTime(BookingTime.fromInstant(START))
                .selectedEndTimeUtc(END)
                .selectedEndTime(BookingTime.fromInstant(END))
                .build();
        session = Session.builder()
                .id(UUID.randomUUID())
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(bookingId)
                .status(SessionStatus.SCHEDULED)
                .build();
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        // The outsider authorization path rejects before loading the session.
        // Keep this shared fixture lenient without weakening the whole test class.
        lenient().when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, bookingId))
                .thenReturn(Optional.of(session));
    }

    @Test
    void firstCheckIn_movesSessionToInProgressWithoutInventingActualStart() {
        service.setTimeProvider(TimeProvider.fixed(START.plusSeconds(30), ZoneOffset.UTC));
        List<SessionAttendance> stored = new ArrayList<>();
        when(attendanceRepository.findBySessionIdAndParticipantRole(session.getId(), SessionParticipantRole.MENTEE))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any(SessionAttendance.class))).thenAnswer(invocation -> {
            SessionAttendance attendance = invocation.getArgument(0);
            stored.add(attendance);
            return attendance;
        });
        when(attendanceRepository.findBySessionId(session.getId())).thenAnswer(invocation -> List.copyOf(stored));

        service.checkIn(menteeId, bookingId);

        assertEquals(SessionStatus.IN_PROGRESS, session.getStatus());
        assertNull(session.getActualStartTimeUtc());
        verify(sessionRepository).save(session);
    }

    @Test
    void secondParticipantCheckIn_recordsTheLaterCheckInAsActualStart() {
        Instant menteeCheckIn = START.plusSeconds(10);
        Instant mentorCheckIn = START.plusSeconds(45);
        service.setTimeProvider(TimeProvider.fixed(mentorCheckIn, ZoneOffset.UTC));
        List<SessionAttendance> stored = new ArrayList<>(List.of(SessionAttendance.builder()
                .session(session)
                .participantRole(SessionParticipantRole.MENTEE)
                .participantUserId(menteeId)
                .checkedInAtUtc(menteeCheckIn)
                .build()));
        when(attendanceRepository.findBySessionIdAndParticipantRole(session.getId(), SessionParticipantRole.MENTOR))
                .thenReturn(Optional.empty());
        when(attendanceRepository.save(any(SessionAttendance.class))).thenAnswer(invocation -> {
            SessionAttendance attendance = invocation.getArgument(0);
            stored.add(attendance);
            return attendance;
        });
        when(attendanceRepository.findBySessionId(session.getId())).thenAnswer(invocation -> List.copyOf(stored));

        service.checkIn(mentorId, bookingId);

        assertEquals(SessionStatus.IN_PROGRESS, session.getStatus());
        assertEquals(mentorCheckIn, session.getActualStartTimeUtc());
    }

    @Test
    void retryAfterCheckIn_returnsSafelyWithoutWritingAnotherAttendance() {
        SessionAttendance existing = SessionAttendance.builder()
                .session(session)
                .participantRole(SessionParticipantRole.MENTOR)
                .participantUserId(mentorId)
                .checkedInAtUtc(START.plusSeconds(5))
                .build();
        session.setStatus(SessionStatus.COMPLETED);
        when(attendanceRepository.findBySessionIdAndParticipantRole(session.getId(), SessionParticipantRole.MENTOR))
                .thenReturn(Optional.of(existing));

        Booking result = service.checkIn(mentorId, bookingId);

        assertEquals(booking, result);
        verify(attendanceRepository, never()).save(any(SessionAttendance.class));
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void checkInOutsideTheSessionWindow_doesNotCreateEvidenceOrStartTheSession() {
        service.setTimeProvider(TimeProvider.fixed(START.minusMillis(1), ZoneOffset.UTC));
        when(attendanceRepository.findBySessionIdAndParticipantRole(session.getId(), SessionParticipantRole.MENTEE))
                .thenReturn(Optional.empty());

        assertThrows(BaseException.class, () -> service.checkIn(menteeId, bookingId));

        assertEquals(SessionStatus.SCHEDULED, session.getStatus());
        verify(attendanceRepository, never()).save(any(SessionAttendance.class));
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void nonParticipantCannotReadOrWriteAttendanceForTheBooking() {
        UUID outsiderId = UUID.randomUUID();

        assertThrows(BaseException.class, () -> service.checkIn(outsiderId, bookingId));

        verify(sessionRepository, never()).findBySourceTypeAndSourceIdForUpdate(any(), any());
        verify(attendanceRepository, never()).save(any(SessionAttendance.class));
    }
}
