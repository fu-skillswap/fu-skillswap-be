package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingWebSocketPublisherTest {

    @Mock
    private RealtimeFanoutService realtimeFanoutService;

    @InjectMocks
    private BookingWebSocketPublisher publisher;

    @Test
    void handleBookingStatusUpdate_shouldPushToBothParticipants() {
        UUID bookingId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        BookingStatus status = BookingStatus.PAID;
        String message = "Thanh toán thành công.";
        LocalDateTime now = LocalDateTime.now();

        BookingStatusUpdatedEvent event = new BookingStatusUpdatedEvent(
                bookingId, menteeId, mentorId, status, message, now
        );

        publisher.handleBookingStatusUpdate(event);

        verify(realtimeFanoutService).pushBookingStatus(menteeId, event);
        verify(realtimeFanoutService).pushBookingStatus(mentorId, event);
    }
}
