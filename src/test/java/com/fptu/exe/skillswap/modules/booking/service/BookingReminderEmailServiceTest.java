package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.projection.PendingBookingServiceCountProjection;
import com.fptu.exe.skillswap.modules.booking.service.BookingReminderEmailService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.notification.port.EmailDispatchPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

class BookingReminderEmailServiceTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final EmailDispatchPort emailDispatchPort = mock(EmailDispatchPort.class);
    private final UserQueryPort userQueryPort = mock(UserQueryPort.class);
    private final BookingReminderEmailService service = new BookingReminderEmailService(bookingRepository, emailDispatchPort);

    {
        ReflectionTestUtils.setField(service, "userQueryPort", userQueryPort);
    }

    @Test
    void sendUpcomingSessionReminders_shouldSendMenteeAndMentorEmailsForConfirmedBookings() {
        Booking booking = confirmedBooking(BookingStatus.PAID);
        when(bookingRepository.findConfirmedBookingsStartingBetweenUtc(any(), any(), any())).thenReturn(List.of(booking));
        when(emailDispatchPort.sendHtmlOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);

        int sent = service.sendUpcomingSessionReminders();

        assertEquals(2, sent);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<BookingStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(bookingRepository).findConfirmedBookingsStartingBetweenUtc(statusesCaptor.capture(), any(), any());
        assertTrue(statusesCaptor.getValue().contains(BookingStatus.PAID));
        assertTrue(statusesCaptor.getValue().contains(BookingStatus.PAID));
        verify(emailDispatchPort).sendHtmlOnce(
                eq("BOOKING_SESSION_REMINDER_MENTEE:" + booking.getId()),
                eq("mentee@test.com"),
                eq("[SkillSwap] Buổi học của bạn bắt đầu sau 30 phút"),
                any(),
                any(),
                eq("BOOKING_SESSION_REMINDER_MENTEE")
        );
        verify(emailDispatchPort).sendHtmlOnce(
                eq("BOOKING_SESSION_REMINDER_MENTOR:" + booking.getId()),
                eq("mentor@test.com"),
                eq("[SkillSwap] Buổi mentoring bắt đầu sau 30 phút"),
                any(),
                any(),
                eq("BOOKING_SESSION_REMINDER_MENTOR")
        );
    }

    @Test
    void sendPendingRequestDigests_shouldGroupPendingRequestsByService() {
        UUID mentorId = UUID.randomUUID();
        when(bookingRepository.countPendingRequestsGroupedByMentorAndService(BookingStatus.PENDING)).thenReturn(List.of(
                new PendingRow(mentorId, "mentor@test.com", "Mentor A", "Review Project", 2),
                new PendingRow(mentorId, "mentor@test.com", "Mentor A", "CV Review", 1)
        ));
        when(userQueryPort.findUserSummaryById(mentorId)).thenReturn(java.util.Optional.of(
                new com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord(
                        mentorId, "mentor@test.com", "Mentor A", null, java.util.Set.of(), "MENTOR", true)));
        when(emailDispatchPort.sendHtmlOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);

        int sent = service.sendPendingRequestDigests();

        assertEquals(1, sent);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailDispatchPort).sendHtmlOnce(
                any(),
                eq("mentor@test.com"),
                eq("[SkillSwap] Bạn có 3 yêu cầu mentoring đang chờ xác nhận"),
                htmlCaptor.capture(),
                plainCaptor.capture(),
                eq("MENTOR_PENDING_REQUEST_DIGEST")
        );
        assertTrue(htmlCaptor.getValue().contains("Review Project"));
        assertTrue(htmlCaptor.getValue().contains("2 yêu cầu"));
        assertTrue(plainCaptor.getValue().contains("- CV Review: 1 yêu cầu"));
    }

    @Test
    void sendPendingRequestDigests_shouldSkipWhenNoPendingRequests() {
        when(bookingRepository.countPendingRequestsGroupedByMentorAndService(BookingStatus.PENDING)).thenReturn(List.of());

        int sent = service.sendPendingRequestDigests();

        assertEquals(0, sent);
        verify(emailDispatchPort, never()).sendHtmlOnce(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendDailyMentorScheduleDigests_shouldGroupAndSendDailyScheduleForMentorsWithBookings() {
        UUID mentor1Id = UUID.randomUUID();
        UUID mentor2Id = UUID.randomUUID();

        when(userQueryPort.findUserSummaryById(mentor1Id)).thenReturn(java.util.Optional.of(
                new com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord(mentor1Id, "mentor1@test.com", "Mentor One", null, java.util.Set.of(), "MENTOR", true)));
        when(userQueryPort.findUserSummaryById(mentor2Id)).thenReturn(java.util.Optional.of(
                new com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord(mentor2Id, "mentor2@test.com", "Mentor Two", null, java.util.Set.of(), "MENTOR", true)));

        UUID menteeId = UUID.randomUUID();
        User mentee = mock(User.class);
        when(mentee.getId()).thenReturn(menteeId);
        when(mentee.getEmail()).thenReturn("mentee@test.com");
        when(mentee.getFullName()).thenReturn("Mentee X");

        Booking booking1 = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.PAID)
                .menteeUserId(mentee.getId())
                .mentorUserId(mentor1Id)
                .serviceTitleSnapshot("Coding 1:1")
                .selectedStartTime(LocalDateTime.now().withHour(9).withMinute(0))
                .selectedEndTime(LocalDateTime.now().withHour(10).withMinute(0))
                .meetingLink("https://meet.google.com/abc-defg-hij")
                .build();

        Booking booking2 = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.PAID)
                .menteeUserId(mentee.getId())
                .mentorUserId(mentor1Id)
                .serviceTitleSnapshot("CV Review")
                .selectedStartTime(LocalDateTime.now().withHour(14).withMinute(0))
                .selectedEndTime(LocalDateTime.now().withHour(15).withMinute(0))
                .build();

        Booking booking3 = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.PAID)
                .menteeUserId(mentee.getId())
                .mentorUserId(mentor2Id)
                .serviceTitleSnapshot("System Design")
                .selectedStartTime(LocalDateTime.now().withHour(16).withMinute(0))
                .selectedEndTime(LocalDateTime.now().withHour(17).withMinute(0))
                .build();

        when(bookingRepository.findConfirmedBookingsStartingBetweenUtc(any(), any(), any()))
                .thenReturn(List.of(booking1, booking2, booking3));
        when(emailDispatchPort.sendHtmlOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);

        int sent = service.sendDailyMentorScheduleDigests();

        assertEquals(2, sent);

        ArgumentCaptor<String> htmlCaptor1 = ArgumentCaptor.forClass(String.class);
        verify(emailDispatchPort).sendHtmlOnce(
                org.mockito.ArgumentMatchers.startsWith("MENTOR_DAILY_SCHEDULE_DIGEST:" + mentor1Id),
                eq("mentor1@test.com"),
                org.mockito.ArgumentMatchers.contains("2 buổi học"),
                htmlCaptor1.capture(),
                any(),
                eq("MENTOR_DAILY_SCHEDULE_DIGEST")
        );
        assertTrue(htmlCaptor1.getValue().contains("Coding 1:1"));
        assertTrue(htmlCaptor1.getValue().contains("CV Review"));

        verify(emailDispatchPort).sendHtmlOnce(
                org.mockito.ArgumentMatchers.startsWith("MENTOR_DAILY_SCHEDULE_DIGEST:" + mentor2Id),
                eq("mentor2@test.com"),
                org.mockito.ArgumentMatchers.contains("1 buổi học"),
                any(),
                any(),
                eq("MENTOR_DAILY_SCHEDULE_DIGEST")
        );
    }

    @Test
    void sendDailyMentorScheduleDigests_shouldSkipWhenNoConfirmedBookingsForToday() {
        when(bookingRepository.findConfirmedBookingsStartingBetweenUtc(any(), any(), any()))
                .thenReturn(List.of());

        int sent = service.sendDailyMentorScheduleDigests();

        assertEquals(0, sent);
        verify(emailDispatchPort, never()).sendHtmlOnce(
                org.mockito.ArgumentMatchers.startsWith("MENTOR_DAILY_SCHEDULE_DIGEST:"),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    private Booking confirmedBooking(BookingStatus status) {
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        User mentee = mock(User.class);
        when(mentee.getId()).thenReturn(menteeId);
        when(mentee.getEmail()).thenReturn("mentee@test.com");
        when(mentee.getFullName()).thenReturn("Mentee A");
        when(userQueryPort.findUserSummaryById(menteeId)).thenReturn(java.util.Optional.of(
                new com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord(
                        menteeId, "mentee@test.com", "Mentee A", null, java.util.Set.of(), "MENTEE", true)));
        when(userQueryPort.findUserSummaryById(mentorId)).thenReturn(java.util.Optional.of(
                new com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord(mentorId, "mentor@test.com", "Mentor A", null, java.util.Set.of(), "MENTOR", true)));
        return Booking.builder()
                .id(UUID.randomUUID())
                .status(status)
                .menteeUserId(mentee.getId())
                .mentorUserId(mentorId)
                .serviceTitleSnapshot("Review Project")
                .serviceDurationSnapshot(60)
                .learningGoalTitle("Gỡ project")
                .learningGoalDescription("Cần review checkpoint")
                .selectedStartTime(LocalDateTime.now().plusMinutes(30))
                .selectedEndTime(LocalDateTime.now().plusMinutes(90))
                .build();
    }

    private record PendingRow(
            UUID mentorUserId,
            String mentorEmail,
            String mentorName,
            String serviceTitle,
            long pendingCount
    ) implements PendingBookingServiceCountProjection {
        @Override
        public UUID getMentorUserId() {
            return mentorUserId;
        }

        @Override
        public String getMentorEmail() {
            return mentorEmail;
        }

        @Override
        public String getMentorName() {
            return mentorName;
        }

        @Override
        public String getServiceTitle() {
            return serviceTitle;
        }

        @Override
        public long getPendingCount() {
            return pendingCount;
        }
    }
}
