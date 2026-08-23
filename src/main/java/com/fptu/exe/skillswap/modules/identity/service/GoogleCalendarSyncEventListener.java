package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.event.GoogleCalendarCancelBookingRequestedEvent;
import com.fptu.exe.skillswap.modules.identity.event.GoogleCalendarCreateBookingRequestedEvent;
import com.fptu.exe.skillswap.modules.identity.event.GoogleCalendarUpdateBookingRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GoogleCalendarSyncEventListener {

    private final GoogleCalendarSyncService googleCalendarSyncService;

    // Persist the sync job with the booking transaction. The worker performs the
    // external Google call later, after commit.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCreateRequested(GoogleCalendarCreateBookingRequestedEvent event) {
        googleCalendarSyncService.enqueueCreate(event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUpdateRequested(GoogleCalendarUpdateBookingRequestedEvent event) {
        googleCalendarSyncService.enqueueUpdate(event.bookingId(), event.bookingUpdatedAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCancelRequested(GoogleCalendarCancelBookingRequestedEvent event) {
        googleCalendarSyncService.enqueueCancel(event.bookingId(), event.status());
    }
}
