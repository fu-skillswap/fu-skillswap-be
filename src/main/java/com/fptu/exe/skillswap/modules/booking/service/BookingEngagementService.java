package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.*;
import com.fptu.exe.skillswap.modules.booking.repository.BookingEngagementDeliveryRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingEngagementService {
    private static final List<BookingStatus> REMINDABLE = List.of(BookingStatus.PAID);
    private final BookingRepository bookingRepository;
    private final BookingEngagementDeliveryRepository deliveryRepository;
    private final NotificationService notificationService;
    private com.fptu.exe.skillswap.shared.time.TimeProvider timeProvider = com.fptu.exe.skillswap.shared.time.TimeProvider.from(java.time.Clock.systemUTC());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTimeProvider(com.fptu.exe.skillswap.shared.time.TimeProvider timeProvider) {
        if (timeProvider != null) this.timeProvider = timeProvider;
    }

    @Transactional
    public int sendScheduledReminders() {
        Instant now = timeProvider.instant();
        return deliverReminderWindow(now, 60, BookingEngagementDeliveryType.REMINDER_1H, "Buổi học bắt đầu sau 1 giờ");
    }

    @Transactional
    public void promptFeedbackIfEligible(UUID bookingId) {
        bookingRepository.findById(bookingId).ifPresent(booking -> {
            if (booking.getStatus() == BookingStatus.COMPLETED && booking.getCompletionOutcome() == BookingCompletionOutcome.USER_CONFIRMED) {
                deliver(booking, booking.getMentee().getId(), BookingEngagementDeliveryType.FEEDBACK_PROMPT,
                        NotificationType.FEEDBACK_PROMPT, "Đánh giá buổi mentoring", "Buổi học đã hoàn tất. Hãy chia sẻ đánh giá của bạn.");
            }
        });
    }

    private int deliverReminderWindow(Instant now, int minutes, BookingEngagementDeliveryType type, String title) {
        List<Booking> bookings = bookingRepository.findConfirmedBookingsStartingBetweenUtc(REMINDABLE,
                now.plus(Duration.ofMinutes(minutes)).minusSeconds(30), now.plus(Duration.ofMinutes(minutes)).plusSeconds(30));
        int count = 0;
        for (Booking booking : bookings) {
            count += deliver(booking, booking.getMentee().getId(), type, NotificationType.BOOKING_REMINDER, title, "Kiểm tra lịch học và chuẩn bị trước giờ bắt đầu.") ? 1 : 0;
            count += deliver(booking, booking.getMentorProfile().getUserId(), type, NotificationType.BOOKING_REMINDER, title, "Kiểm tra lịch mentoring và chuẩn bị trước giờ bắt đầu.") ? 1 : 0;
        }
        return count;
    }

    private boolean deliver(Booking booking, UUID recipientId, BookingEngagementDeliveryType type, NotificationType notificationType, String title, String message) {
        if (booking == null || recipientId == null || deliveryRepository.existsByBookingIdAndRecipientUserIdAndDeliveryType(booking.getId(), recipientId, type)) return false;
        deliveryRepository.save(BookingEngagementDelivery.builder().booking(booking).recipientUser(booking.getMentee().getId().equals(recipientId)
                ? booking.getMentee() : booking.getMentorProfile().getUser()).deliveryType(type).build());
        notificationService.createNotification(recipientId, notificationType, title, message, "BOOKING", booking.getId(), "/bookings/" + booking.getId());
        return true;
    }
}
