package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeFanoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingWebSocketPublisher {

    private final RealtimeFanoutService realtimeFanoutService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingStatusUpdate(BookingStatusUpdatedEvent event) {
        try {
            log.info("Pushing booking status update via STOMP. bookingId={}, status={}", event.bookingId(), event.status());
            realtimeFanoutService.pushBookingStatus(event.menteeUserId(), event);
            realtimeFanoutService.pushBookingStatus(event.mentorUserId(), event);
        } catch (Exception ex) {
            log.error("Failed to push STOMP update for booking {}", event.bookingId(), ex);
        }
    }
}
