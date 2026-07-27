package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.service.BookingEngagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BookingFeedbackPromptListener {
    private final BookingEngagementService bookingEngagementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusUpdated(BookingStatusUpdatedEvent event) {
        if (event.status() == BookingStatus.COMPLETED) bookingEngagementService.promptFeedbackIfEligible(event.bookingId());
    }
}
