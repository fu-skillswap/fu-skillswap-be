package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.projection.PendingBookingServiceCountProjection;
import com.fptu.exe.skillswap.modules.booking.service.BookingReminderEmailService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mail.service.EmailDispatchService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
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

class BookingReminderEmailServiceTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final EmailDispatchService emailDispatchService = mock(EmailDispatchService.class);
    private final BookingReminderEmailService service = new BookingReminderEmailService(bookingRepository, emailDispatchService);

    @Test
    void sendUpcomingSessionReminders_shouldSendMenteeAndMentorEmailsForConfirmedBookings() {
        Booking booking = confirmedBooking(BookingStatus.PAID);
        when(bookingRepository.findConfirmedBookingsStartingBetween(any(), any(), any())).thenReturn(List.of(booking));
        when(emailDispatchService.sendHtmlOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);

        int sent = service.sendUpcomingSessionReminders();

        assertEquals(2, sent);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<BookingStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(bookingRepository).findConfirmedBookingsStartingBetween(statusesCaptor.capture(), any(), any());
        assertTrue(statusesCaptor.getValue().contains(BookingStatus.ACCEPTED));
        assertTrue(statusesCaptor.getValue().contains(BookingStatus.PAID));
        verify(emailDispatchService).sendHtmlOnce(
                eq("BOOKING_SESSION_REMINDER_MENTEE:" + booking.getId()),
                eq("mentee@test.com"),
                eq("[SkillSwap] Buổi học của bạn bắt đầu sau 30 phút"),
                any(),
                any(),
                eq("BOOKING_SESSION_REMINDER_MENTEE")
        );
        verify(emailDispatchService).sendHtmlOnce(
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
        when(emailDispatchService.sendHtmlOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);

        int sent = service.sendPendingRequestDigests();

        assertEquals(1, sent);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailDispatchService).sendHtmlOnce(
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
        verify(emailDispatchService, never()).sendHtmlOnce(any(), any(), any(), any(), any(), any());
    }

    private Booking confirmedBooking(BookingStatus status) {
        User mentee = User.builder()
                .id(UUID.randomUUID())
                .email("mentee@test.com")
                .fullName("Mentee A")
                .build();
        User mentorUser = User.builder()
                .id(UUID.randomUUID())
                .email("mentor@test.com")
                .fullName("Mentor A")
                .build();
        MentorProfile mentorProfile = MentorProfile.builder()
                .user(mentorUser)
                .build();
        return Booking.builder()
                .id(UUID.randomUUID())
                .status(status)
                .mentee(mentee)
                .mentorProfile(mentorProfile)
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
