package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.infrastructure.config.GroupSessionProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupAttendanceStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionAttendanceRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionExperienceService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.booking.service.SessionService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupSessionExperienceServiceTest {

    @Test
    void submitAttendance_marksPresentSeatAwaitingLearnerConfirmation() {
        GroupSessionRepository groups = mock(GroupSessionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        SessionService sessions = mock(SessionService.class);
        ConversationService conversations = mock(ConversationService.class);
        SettlementService settlement = mock(SettlementService.class);
        GroupSessionProperties properties = new GroupSessionProperties();
        GroupSessionExperienceService service = new GroupSessionExperienceService(groups, bookings, sessions, conversations, settlement, properties);

        UUID groupId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        GroupSession group = GroupSession.builder().id(groupId).version(0)
                .scheduledStartAt(LocalDateTime.now().minusHours(2)).scheduledEndAt(LocalDateTime.now().minusMinutes(5)).build();
        Booking booking = Booking.builder().id(bookingId).status(BookingStatus.PAID).build();
        when(groups.findOwnedByIdForUpdate(groupId, mentorId)).thenReturn(Optional.of(group));
        when(bookings.findGroupSeatBookingsForUpdate(eq(groupId), anyList())).thenReturn(List.of(booking));

        service.submitAttendance(mentorId, groupId, new GroupSessionAttendanceRequest(0,
                List.of(new GroupSessionAttendanceRequest.Attendee(bookingId, GroupAttendanceStatus.PRESENT))));

        assertEquals(BookingStatus.AWAITING_MENTEE_CONFIRMATION, booking.getStatus());
        assertEquals(GroupAttendanceStatus.PRESENT, booking.getGroupAttendanceStatus());
        verify(bookings).saveAll(List.of(booking));
    }

    @Test
    void submitAttendance_rejectsIncompleteRoster() {
        GroupSessionRepository groups = mock(GroupSessionRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        GroupSessionExperienceService service = new GroupSessionExperienceService(groups, bookings, mock(SessionService.class),
                mock(ConversationService.class), mock(SettlementService.class), new GroupSessionProperties());
        UUID groupId = UUID.randomUUID();
        GroupSession group = GroupSession.builder().id(groupId).version(0)
                .scheduledStartAt(LocalDateTime.now().minusHours(2)).scheduledEndAt(LocalDateTime.now().minusMinutes(5)).build();
        when(groups.findOwnedByIdForUpdate(eq(groupId), any())).thenReturn(Optional.of(group));
        when(bookings.findGroupSeatBookingsForUpdate(eq(groupId), anyList())).thenReturn(List.of(
                Booking.builder().id(UUID.randomUUID()).status(BookingStatus.PAID).build()));

        assertThrows(BaseException.class, () -> service.submitAttendance(UUID.randomUUID(), groupId,
                new GroupSessionAttendanceRequest(0, List.of(new GroupSessionAttendanceRequest.Attendee(UUID.randomUUID(), GroupAttendanceStatus.PRESENT)))));
        verify(bookings, never()).saveAll(anyList());
    }
}
