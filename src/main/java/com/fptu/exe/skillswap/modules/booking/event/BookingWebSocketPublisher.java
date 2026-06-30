package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
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

    private final RealtimePushService pushService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingStatusUpdate(BookingStatusUpdatedEvent event) {
        try {
            log.info("Pushing booking status update via WebSocket. bookingId={}, status={}", event.bookingId(), event.status());
            pushService.pushToUser(event.menteeUserId(), RealtimeMessageType.BOOKING_STATUS_UPDATED, event);
            pushService.pushToUser(event.mentorUserId(), RealtimeMessageType.BOOKING_STATUS_UPDATED, event);
        } catch (Exception ex) {
            log.error("Failed to push websocket update for booking {}", event.bookingId(), ex);
        }
    }
}
