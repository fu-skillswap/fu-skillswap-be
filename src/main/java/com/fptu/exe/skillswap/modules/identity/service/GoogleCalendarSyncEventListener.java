package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.booking.event.BookingCalendarLifecycleEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GoogleCalendarSyncEventListener {

    private final GoogleCalendarSyncService googleCalendarSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingLifecycle(BookingCalendarLifecycleEvent event) {
        if (event == null || event.bookingId() == null) return;
        switch (event.action()) {
            case CREATE -> googleCalendarSyncService.enqueueCreate(event.bookingId());
            case UPDATE -> googleCalendarSyncService.enqueueUpdate(event.bookingId(), event.occurredAtUtc());
            case CANCEL -> googleCalendarSyncService.enqueueCancel(event.bookingId());
        }
    }
}
