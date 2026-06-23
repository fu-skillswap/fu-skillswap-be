package com.fptu.exe.skillswap.modules.session;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.session.domain.Session;
import com.fptu.exe.skillswap.modules.session.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.session.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.session.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceUnitTest {

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private SessionService sessionService;

    private Booking booking;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        User mentor = new User();
        mentor.setId(UUID.randomUUID());
        mentor.setFullName("Mentor Fullname");

        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentor.getId());
        mentorProfile.setUser(mentor);

        booking = Booking.builder()
                .id(bookingId)
                .mentorProfile(mentorProfile)
                .requestedStartTime(LocalDateTime.now().plusHours(1))
                .requestedEndTime(LocalDateTime.now().plusHours(2))
                .build();
    }

    @Test
    void createForAcceptedBooking_shouldReturnExisting_whenSessionAlreadyExists() {
        Session existingSession = Session.builder()
                .id(UUID.randomUUID())
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(bookingId)
                .build();

        when(sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, bookingId))
                .thenReturn(Optional.of(existingSession));

        Session result = sessionService.createForAcceptedBooking(booking);

        assertNotNull(result);
        assertEquals(existingSession.getId(), result.getId());
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void createForAcceptedBooking_shouldRecoverFromConcurrencyConflict() {
        Session existingSession = Session.builder()
                .id(UUID.randomUUID())
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(bookingId)
                .build();

        // First find returns empty
        when(sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, bookingId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingSession)); // Second find in catch block recovers it

        // Save fails due to DataIntegrityViolationException
        when(sessionRepository.save(any(Session.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        Session result = sessionService.createForAcceptedBooking(booking);

        assertNotNull(result);
        assertEquals(existingSession.getId(), result.getId());
        verify(sessionRepository, times(1)).save(any(Session.class));
    }
}
