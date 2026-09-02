package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingActivityCommandPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionFinalizationServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private SessionService sessionService;
    @Mock private MentorBookingActivityCommandPort mentorBookingActivityCommandPort;

    private SessionFinalizationService service;
    private Booking booking;
    private MentorProfile mentor;
    private Session session;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        service = new SessionFinalizationService(sessionRepository, sessionService, mentorBookingActivityCommandPort);
        now = LocalDateTime.of(2026, 8, 24, 10, 0);

        UUID mentorId = UUID.randomUUID();
        User mentorUser = new User();
        mentorUser.setId(mentorId);
        mentor = MentorProfile.builder().userId(mentorId)
                .totalSessions(4).totalCompletedSessions(3).build();
        booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorId)
                .selectedStartTime(now.minusHours(2))
                .selectedEndTime(now.minusHours(1))
                .build();
        session = Session.builder()
                .id(UUID.randomUUID())
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(booking.getId())
                .status(SessionStatus.SCHEDULED)
                .build();

        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            mentor.setTotalSessions(mentor.getTotalSessions() + 1);
            mentor.setTotalCompletedSessions(mentor.getTotalCompletedSessions() + 1);
            return null;
        }).when(mentorBookingActivityCommandPort).recordCompletedSession(any(UUID.class), any());
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            mentor.setLastActiveAt(BookingTime.fromInstant(invocation.getArgument(1, java.time.Instant.class)));
            return null;
        }).when(mentorBookingActivityCommandPort).recordMentorActivity(any(UUID.class), any());
    }

    @Test
    void finalizeDeliveredSession_shouldCompleteSessionAndCountMentorExactlyOnce() {
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId()))
                .thenReturn(Optional.of(session));
        service.finalizeDeliveredSession(booking, now);

        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertNull(session.getActualStartTime());
        assertEquals(booking.getSelectedEndTime(), session.getActualEndTime());
        assertEquals(booking.getSelectedEndTime(), booking.getActualEndTime());
        assertEquals(now, booking.getFinalizedAt());
        assertEquals(now, booking.getCompletedAt());
        assertEquals(5, mentor.getTotalSessions());
        assertEquals(4, mentor.getTotalCompletedSessions());
        verify(sessionRepository).save(session);
        verify(mentorBookingActivityCommandPort).recordCompletedSession(eq(mentor.getUserId()), any());
    }

    @Test
    void finalizeDeliveredSession_whenAlreadyFinalized_shouldRepairSessionWithoutCountingAgain() {
        booking.setFinalizedAt(now.minusMinutes(1));
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId()))
                .thenReturn(Optional.of(session));

        service.finalizeDeliveredSession(booking, now);

        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertEquals(4, mentor.getTotalSessions());
        assertEquals(3, mentor.getTotalCompletedSessions());
        verifyNoInteractions(mentorBookingActivityCommandPort);
    }

    @Test
    void recordMentorReportedCompletion_shouldNotFinalizeSessionOrIncreaseCounters() {
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId()))
                .thenReturn(Optional.of(session));
        service.recordMentorReportedCompletion(booking, now);

        assertEquals(SessionStatus.SCHEDULED, session.getStatus());
        assertNull(booking.getCompletedAt());
        assertNull(booking.getFinalizedAt());
        assertEquals(4, mentor.getTotalSessions());
        assertEquals(3, mentor.getTotalCompletedSessions());
        assertEquals(now, mentor.getLastActiveAt());
    }

    @Test
    void finalizeDeliveredSession_doesNotInventAnEndBeforeTheScheduledSessionEnds() {
        session.setScheduledEndTime(now.plusMinutes(15));
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId()))
                .thenReturn(Optional.of(session));
        service.finalizeDeliveredSession(booking, now);

        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        assertNull(session.getActualEndTime());
        assertNull(booking.getActualEndTime());
    }

    @Test
    void finalizeDeliveredSession_mirrorsAttendanceStartToTheLegacyBookingRecord() {
        session.setActualStartTime(booking.getSelectedStartTime().plusMinutes(2));
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId()))
                .thenReturn(Optional.of(session));
        service.finalizeDeliveredSession(booking, now);

        assertEquals(session.getActualStartTime(), booking.getActualStartTime());
        assertEquals(booking.getSelectedEndTime(), booking.getActualEndTime());
    }

    @Test
    void markSessionNotDelivered_shouldCancelSessionAndClearActualTimes() {
        session.setStatus(SessionStatus.COMPLETED);
        session.setActualStartTime(booking.getSelectedStartTime());
        session.setActualEndTime(booking.getSelectedEndTime());
        booking.setActualStartTime(booking.getSelectedStartTime());
        booking.setActualEndTime(booking.getSelectedEndTime());
        booking.setCompletedAt(now);
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId()))
                .thenReturn(Optional.of(session));

        service.markSessionNotDelivered(booking);

        assertEquals(SessionStatus.CANCELLED, session.getStatus());
        assertNull(session.getActualStartTime());
        assertNull(session.getActualEndTime());
        assertNull(booking.getActualStartTime());
        assertNull(booking.getActualEndTime());
        assertNull(booking.getCompletedAt());
    }
}
