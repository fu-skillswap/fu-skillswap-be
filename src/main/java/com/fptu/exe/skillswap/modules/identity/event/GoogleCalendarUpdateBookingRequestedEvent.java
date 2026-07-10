package com.fptu.exe.skillswap.modules.identity.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record GoogleCalendarUpdateBookingRequestedEvent(UUID bookingId, LocalDateTime bookingUpdatedAt) {
}
