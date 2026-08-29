package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEngagementDeliveryType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingEngagementDeliveryRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BookingEngagementServiceTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingEngagementDeliveryRepository deliveryRepository = mock(BookingEngagementDeliveryRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private final BookingEngagementService service = new BookingEngagementService(
            bookingRepository,
            deliveryRepository,
            notificationService
    );

    @Test
    void sendScheduledReminders_shouldOnlyTrigger1HReminderForMenteeAndMentor() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorUserId = UUID.randomUUID();

        User mentee = User.builder().id(menteeId).fullName("Mentee A").build();
        User mentorUser = User.builder().id(mentorUserId).fullName("Mentor A").build();
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentorUserId).user(mentorUser).build();

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.PAID)
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .selectedStartTime(LocalDateTime.now().plusHours(1))
                .selectedEndTime(LocalDateTime.now().plusHours(2))
                .build();

        when(bookingRepository.findConfirmedBookingsStartingBetweenUtc(any(), any(), any()))
                .thenReturn(List.of(booking));
        when(deliveryRepository.existsByBookingIdAndRecipientUserIdAndDeliveryType(any(), any(), any()))
                .thenReturn(false);

        int sent = service.sendScheduledReminders();

        assertEquals(2, sent); // 1 for mentee, 1 for mentor

        // Verify repository query was called exactly ONCE (for the 60 min window)
        verify(bookingRepository, times(1)).findConfirmedBookingsStartingBetweenUtc(any(), any(), any());

        // Verify delivery records saved for REMINDER_1H
        verify(deliveryRepository, times(2)).save(any());

        // Verify in-app notifications created
        verify(notificationService).createNotification(
                eq(menteeId),
                eq(NotificationType.BOOKING_REMINDER),
                eq("Buổi học bắt đầu sau 1 giờ"),
                eq("Kiểm tra lịch học và chuẩn bị trước giờ bắt đầu."),
                eq("BOOKING"),
                eq(booking.getId()),
                eq("/bookings/" + booking.getId())
        );

        verify(notificationService).createNotification(
                eq(mentorUserId),
                eq(NotificationType.BOOKING_REMINDER),
                eq("Buổi học bắt đầu sau 1 giờ"),
                eq("Kiểm tra lịch mentoring và chuẩn bị trước giờ bắt đầu."),
                eq("BOOKING"),
                eq(booking.getId()),
                eq("/bookings/" + booking.getId())
        );
    }

    @Test
    void sendScheduledReminders_shouldSkipIfAlreadyDelivered() {
        UUID menteeId = UUID.randomUUID();
        UUID mentorUserId = UUID.randomUUID();

        User mentee = User.builder().id(menteeId).fullName("Mentee A").build();
        User mentorUser = User.builder().id(mentorUserId).fullName("Mentor A").build();
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentorUserId).user(mentorUser).build();

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .status(BookingStatus.PAID)
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .selectedStartTime(LocalDateTime.now().plusHours(1))
                .selectedEndTime(LocalDateTime.now().plusHours(2))
                .build();

        when(bookingRepository.findConfirmedBookingsStartingBetweenUtc(any(), any(), any()))
                .thenReturn(List.of(booking));
        when(deliveryRepository.existsByBookingIdAndRecipientUserIdAndDeliveryType(eq(booking.getId()), any(), eq(BookingEngagementDeliveryType.REMINDER_1H)))
                .thenReturn(true);

        int sent = service.sendScheduledReminders();

        assertEquals(0, sent);
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }
}
