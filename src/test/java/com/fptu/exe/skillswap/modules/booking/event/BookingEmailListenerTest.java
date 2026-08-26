package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.notification.service.EmailDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class BookingEmailListenerTest {

    @Mock private EmailDispatchService emailDispatchService;

    @Test
    void queueFailure_mustPropagateSoTheBookingTransactionDoesNotCommitWithoutEmailIntent() {
        BookingEmailListener listener = new BookingEmailListener(emailDispatchService);
        doThrow(new IllegalStateException("email outbox unavailable"))
                .when(emailDispatchService).queueHtmlOnce(any(), any(), any(), any(), any(), any());

        BookingEmailNotificationEvent event = BookingEmailNotificationEvent.builder()
                .bookingId(UUID.randomUUID())
                .eventType(BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_REPORTED_EMAIL)
                .recipientEmail("mentor@skillswap.vn")
                .recipientName("Mentor")
                .build();

        assertThrows(IllegalStateException.class, () -> listener.handleBookingEmailNotification(event));
    }
}
