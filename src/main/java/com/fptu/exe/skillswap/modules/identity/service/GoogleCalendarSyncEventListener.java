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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreateRequested(GoogleCalendarCreateBookingRequestedEvent event) {
        googleCalendarSyncService.enqueueCreate(event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpdateRequested(GoogleCalendarUpdateBookingRequestedEvent event) {
        googleCalendarSyncService.enqueueUpdate(event.bookingId(), event.bookingUpdatedAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelRequested(GoogleCalendarCancelBookingRequestedEvent event) {
        googleCalendarSyncService.enqueueCancel(event.bookingId(), event.status());
    }
}
