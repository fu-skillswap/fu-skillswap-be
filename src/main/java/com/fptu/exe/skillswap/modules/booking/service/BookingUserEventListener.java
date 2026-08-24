package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import com.fptu.exe.skillswap.shared.event.UserDeletedEvent;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.Clock;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingUserEventListener {

    private final BookingService bookingService;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserBanned(UserBannedEvent event) {
        log.info("Booking module: Handling UserBannedEvent for mentor: {}", event.getUserId());

        // 1. Deactivate future unbooked slots
        int deactivatedCount = mentorAvailabilitySlotRepository.deactivateFutureUnbookedSlots(event.getUserId(), currentTime());
        log.info("Deactivated {} future unbooked slots for banned mentor: {}", deactivatedCount, event.getUserId());

        // 2. Reject all pending bookings
        bookingService.rejectAllPendingBookingsForMentor(event.getUserId(), "Tài khoản mentor đã bị đình chỉ hoạt động");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Booking module: Handling UserDeletedEvent for mentor: {}", event.getUserId());

        // 1. Deactivate future unbooked slots
        int deactivatedCount = mentorAvailabilitySlotRepository.deactivateFutureUnbookedSlots(event.getUserId(), currentTime());
        log.info("Deactivated {} future unbooked slots for deleted mentor: {}", deactivatedCount, event.getUserId());

        // 2. Reject all pending bookings
        bookingService.rejectAllPendingBookingsForMentor(event.getUserId(), "Tài khoản mentor đã bị xóa khỏi hệ thống");
    }

    private Instant currentTime() {
        return timeProvider.instant();
    }
}
